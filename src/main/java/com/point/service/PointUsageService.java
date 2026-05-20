package com.point.service;

import com.point.support.PointKeyGenerator;
import com.point.policy.PointPolicyProvider;
import com.point.support.TimeProvider;
import com.point.domain.entity.*;
import com.point.domain.enums.*;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointUsageService {

    private static final int GRANT_FETCH_SIZE = 100;

    private final PointAccountRepository pointAccountRepository;
    private final PointGrantRepository pointGrantRepository;
    private final PointUsageRepository pointUsageRepository;
    private final PointUsageDetailRepository pointUsageDetailRepository;
    private final PointUsageCancelRepository pointUsageCancelRepository;
    private final PointUsageCancelDetailRepository pointUsageCancelDetailRepository;
    private final PointPolicyProvider policyProvider;
    private final PointKeyGenerator pointKeyGenerator;
    private final PointExpirationService pointExpirationService;
    private final TimeProvider timeProvider;

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class)
    @Transactional
    public PointUsage use(String userId, String orderId, String pointKey, long amount) {
        var existingUsage = pointUsageRepository.findByPointKey(pointKey);
        if (existingUsage.isPresent()) {
            PointUsage existing = existingUsage.get();
            if (isSameUsageRequest(existing, userId, orderId, amount)) {
                return existing;
            }
            throw new PointException(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
        }

        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));

        LocalDate today = timeProvider.today();
        pointExpirationService.expire(account, today);
        if (account.getBalance() < amount) {
            throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE);
        }

        PointUsage usage = PointUsage.builder()
                .pointKey(pointKey)
                .pointAccount(account)
                .userId(userId)
                .orderId(orderId)
                .usedAmount(amount)
                .build();
        pointUsageRepository.save(usage);

        long remaining = amount;
        int sequence = 1;
        while (remaining > 0) {
            List<PointGrant> usableGrants = pointGrantRepository.findUsableGrants(
                    account.getId(),
                    today,
                    PageRequest.of(0, GRANT_FETCH_SIZE)
            );
            if (usableGrants.isEmpty()) {
                throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE);
            }

            for (PointGrant grant : usableGrants) {
                if (remaining <= 0) break;

                long deduct = Math.min(remaining, grant.getRemainingAmount());
                grant.useAmount(deduct);

                pointUsageDetailRepository.save(PointUsageDetail.builder()
                        .pointUsage(usage)
                        .pointGrant(grant)
                        .useSequence(sequence++)
                        .usedAmount(deduct)
                        .build());

                remaining -= deduct;
            }
        }

        account.decreaseBalance(amount);

        return usage;
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class)
    @Transactional
    public PointUsageCancel cancel(String usagePointKey, long cancelAmount) {
        PointUsage usage = pointUsageRepository.findByPointKey(usagePointKey)
                .orElseThrow(() -> new PointException(PointErrorCode.USAGE_NOT_FOUND));

        if (usage.getStatus() == UsageStatus.CANCELLED) {
            throw new PointException(PointErrorCode.USAGE_ALREADY_CANCELLED);
        }
        if (cancelAmount > usage.getRemainCancelableAmount()) {
            throw new PointException(PointErrorCode.CANCEL_AMOUNT_EXCEEDS_REMAINING);
        }

        PointAccount account = pointAccountRepository.findByUserId(usage.getUserId())
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));

        PointUsageCancel usageCancel = PointUsageCancel.builder()
                .pointKey(pointKeyGenerator.generate("cancel"))
                .pointUsage(usage)
                .cancelAmount(cancelAmount)
                .build();
        pointUsageCancelRepository.save(usageCancel);

        List<PointUsageDetail> details = pointUsageDetailRepository
                .findByPointUsageIdOrderByUseSequenceAsc(usage.getId());
        Map<Long, Long> cancelledAmountByDetailId = loadCancelledAmountByDetailId(details);

        LocalDate today = timeProvider.today();
        restoreUsedPoints(account, usage, usageCancel, details, cancelledAmountByDetailId, today, cancelAmount);

        usage.cancel(cancelAmount);
        account.increaseBalance(cancelAmount);

        return usageCancel;
    }

    private void restoreUsedPoints(PointAccount account,
                                   PointUsage usage,
                                   PointUsageCancel usageCancel,
                                   List<PointUsageDetail> details,
                                   Map<Long, Long> cancelledAmountByDetailId,
                                   LocalDate today,
                                   long cancelAmount) {
        long remaining = cancelAmount;
        for (PointUsageDetail detail : details) {
            if (remaining <= 0) break;

            long cancelFromDetail = calculateCancelableAmount(detail, cancelledAmountByDetailId, remaining);
            if (cancelFromDetail <= 0) continue;

            RestoreResult restoreResult = restorePoint(account, usage, usageCancel, detail, today, cancelFromDetail);

            pointUsageCancelDetailRepository.save(PointUsageCancelDetail.builder()
                    .pointUsageCancel(usageCancel)
                    .pointUsageDetail(detail)
                    .cancelAmount(cancelFromDetail)
                    .restoreType(restoreResult.restoreType())
                    .restoredPointGrant(restoreResult.restoredGrant().orElse(null))
                    .build());

            remaining -= cancelFromDetail;
        }
    }

    private Map<Long, Long> loadCancelledAmountByDetailId(List<PointUsageDetail> details) {
        return pointUsageCancelDetailRepository
                .sumCancelAmountByUsageDetailIds(details.stream().map(PointUsageDetail::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        PointUsageCancelDetailRepository.CancelledAmountView::getUsageDetailId,
                        PointUsageCancelDetailRepository.CancelledAmountView::getCancelledAmount
                ));
    }

    private long calculateCancelableAmount(PointUsageDetail detail,
                                           Map<Long, Long> cancelledAmountByDetailId,
                                           long remainingCancelAmount) {
        long alreadyCancelled = cancelledAmountByDetailId.getOrDefault(detail.getId(), 0L);
        long available = detail.getUsedAmount() - alreadyCancelled;
        return Math.min(remainingCancelAmount, available);
    }

    private RestoreResult restorePoint(PointAccount account,
                                       PointUsage usage,
                                       PointUsageCancel usageCancel,
                                       PointUsageDetail detail,
                                       LocalDate today,
                                       long amount) {
        PointGrant grant = detail.getPointGrant();
        if (canRestoreToOriginalGrant(grant, today)) {
            grant.restoreAmount(amount);
            return RestoreResult.originalGrant();
        }

        PointGrant restoredGrant = createRestoredGrant(account, usage, usageCancel, today, amount);
        return RestoreResult.newGrant(restoredGrant);
    }

    private boolean canRestoreToOriginalGrant(PointGrant grant, LocalDate today) {
        return grant.getStatus() == GrantStatus.ACTIVE && !grant.isExpired(today);
    }

    private PointGrant createRestoredGrant(PointAccount account,
                                           PointUsage usage,
                                           PointUsageCancel usageCancel,
                                           LocalDate today,
                                           long amount) {
        long expiryDays = policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS);
        PointGrant restoredGrant = PointGrant.builder()
                .pointKey(pointKeyGenerator.generate("grant"))
                .pointAccount(account)
                .userId(usage.getUserId())
                .originalAmount(amount)
                .grantType(GrantType.CANCEL_RESTORE)
                .expiryDate(today.plusDays(expiryDays))
                .sourceUsageCancel(usageCancel)
                .build();
        return pointGrantRepository.save(restoredGrant);
    }

    private boolean isSameUsageRequest(PointUsage existing, String userId, String orderId, long amount) {
        return existing.getUserId().equals(userId)
                && existing.getOrderId().equals(orderId)
                && existing.getUsedAmount() == amount;
    }

    private record RestoreResult(RestoreType restoreType, Optional<PointGrant> restoredGrant) {

        static RestoreResult originalGrant() {
            return new RestoreResult(RestoreType.RESTORE_TO_ORIGINAL, Optional.empty());
        }

        static RestoreResult newGrant(PointGrant restoredGrant) {
            return new RestoreResult(RestoreType.CREATE_NEW_GRANT, Optional.of(restoredGrant));
        }
    }
}
