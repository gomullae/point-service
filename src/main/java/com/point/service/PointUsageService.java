package com.point.service;

import com.point.common.PointKeyGenerator;
import com.point.common.PointPolicyProvider;
import com.point.common.TimeProvider;
import com.point.domain.entity.*;
import com.point.domain.enums.*;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointUsageService {

    private final PointAccountRepository pointAccountRepository;
    private final PointGrantRepository pointGrantRepository;
    private final PointUsageRepository pointUsageRepository;
    private final PointUsageDetailRepository pointUsageDetailRepository;
    private final PointUsageCancelRepository pointUsageCancelRepository;
    private final PointUsageCancelDetailRepository pointUsageCancelDetailRepository;
    private final PointPolicyProvider policyProvider;
    private final PointKeyGenerator pointKeyGenerator;
    private final TimeProvider timeProvider;

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class)
    @Transactional
    public PointUsage use(String userId, String orderId, String pointKey, long amount) {
        var existingUsage = pointUsageRepository.findByPointKey(pointKey);
        if (existingUsage.isPresent()) {
            PointUsage existing = existingUsage.get();
            if (existing.getUserId().equals(userId)
                    && existing.getOrderId().equals(orderId)
                    && existing.getUsedAmount() == amount) {
                return existing;
            }
            throw new PointException(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
        }

        PointAccount account = pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));

        LocalDate today = timeProvider.today();
        List<PointGrant> usableGrants = pointGrantRepository.findUsableGrants(account.getId(), today);
        long usableBalance = usableGrants.stream()
                .mapToLong(PointGrant::getRemainingAmount)
                .sum();
        if (usableBalance < amount) {
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

        if (remaining != 0) {
            throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE);
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
        Map<Long, Long> cancelledAmountByDetailId = pointUsageCancelDetailRepository
                .sumCancelAmountByUsageDetailIds(details.stream().map(PointUsageDetail::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        PointUsageCancelDetailRepository.CancelledAmountView::getUsageDetailId,
                        PointUsageCancelDetailRepository.CancelledAmountView::getCancelledAmount
                ));

        LocalDate today = timeProvider.today();
        long remaining = cancelAmount;

        for (PointUsageDetail detail : details) {
            if (remaining <= 0) break;

            long alreadyCancelled = cancelledAmountByDetailId.getOrDefault(detail.getId(), 0L);
            long available = detail.getUsedAmount() - alreadyCancelled;
            if (available <= 0) continue;

            long cancelFromDetail = Math.min(remaining, available);
            PointGrant grant = detail.getPointGrant();

            RestoreType restoreType;
            PointGrant restoredGrant = null;

            if (grant.getStatus() == GrantStatus.ACTIVE && !grant.isExpired(today)) {
                restoreType = RestoreType.RESTORE_TO_ORIGINAL;
                grant.restoreAmount(cancelFromDetail);
            } else {
                restoreType = RestoreType.CREATE_NEW_GRANT;
                long expiryDays = policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS);
                restoredGrant = PointGrant.builder()
                        .pointKey(pointKeyGenerator.generate("grant"))
                        .pointAccount(account)
                        .userId(usage.getUserId())
                        .originalAmount(cancelFromDetail)
                        .grantType(GrantType.CANCEL_RESTORE)
                        .expiryDate(today.plusDays(expiryDays))
                        .sourceUsageCancel(usageCancel)
                        .build();
                pointGrantRepository.save(restoredGrant);
            }

            pointUsageCancelDetailRepository.save(PointUsageCancelDetail.builder()
                    .pointUsageCancel(usageCancel)
                    .pointUsageDetail(detail)
                    .cancelAmount(cancelFromDetail)
                    .restoreType(restoreType)
                    .restoredPointGrant(restoredGrant)
                    .build());

            remaining -= cancelFromDetail;
        }

        usage.cancel(cancelAmount);
        account.increaseBalance(cancelAmount);

        return usageCancel;
    }
}
