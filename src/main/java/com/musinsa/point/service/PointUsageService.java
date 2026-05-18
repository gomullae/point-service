package com.musinsa.point.service;

import com.musinsa.point.common.PointKeyGenerator;
import com.musinsa.point.common.PointPolicyProvider;
import com.musinsa.point.common.TimeProvider;
import com.musinsa.point.domain.entity.*;
import com.musinsa.point.domain.enums.*;
import com.musinsa.point.exception.PointErrorCode;
import com.musinsa.point.exception.PointException;
import com.musinsa.point.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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

    @Transactional
    public PointUsage use(String userId, String orderId, String pointKey, long amount) {
        if (pointUsageRepository.existsByPointKey(pointKey)) {
            throw new PointException(PointErrorCode.DUPLICATE_POINT_KEY);
        }

        PointAccount account = pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getBalance() < amount) {
            throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE);
        }

        LocalDate today = timeProvider.today();
        List<PointGrant> usableGrants = pointGrantRepository.findUsableGrants(account.getId(), today);

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

        account.decreaseBalance(amount);

        return usage;
    }

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

        PointAccount account = pointAccountRepository.findByUserIdForUpdate(usage.getUserId())
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));

        PointUsageCancel usageCancel = PointUsageCancel.builder()
                .pointKey(pointKeyGenerator.generate("cancel"))
                .pointUsage(usage)
                .cancelAmount(cancelAmount)
                .build();
        pointUsageCancelRepository.save(usageCancel);

        List<PointUsageDetail> details = pointUsageDetailRepository
                .findByPointUsageIdOrderByUseSequenceAsc(usage.getId());

        LocalDate today = timeProvider.today();
        long remaining = cancelAmount;

        for (PointUsageDetail detail : details) {
            if (remaining <= 0) break;

            long alreadyCancelled = pointUsageCancelDetailRepository
                    .sumCancelAmountByUsageDetailId(detail.getId());
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
