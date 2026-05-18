package com.point.service;

import com.point.common.PointKeyGenerator;
import com.point.common.PointPolicyProvider;
import com.point.common.TimeProvider;
import com.point.domain.entity.*;
import com.point.domain.enums.*;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.PointAccountRepository;
import com.point.repository.PointGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointGrantService {

    private final PointAccountRepository pointAccountRepository;
    private final PointGrantRepository pointGrantRepository;
    private final PointPolicyProvider policyProvider;
    private final PointKeyGenerator pointKeyGenerator;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public PointAccount getAccount(String userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public long getUsableBalance(String userId) {
        PointAccount account = getAccount(userId);
        return pointGrantRepository.sumUsableBalance(account.getId(), timeProvider.today());
    }

    @Transactional(readOnly = true)
    public List<PointGrant> getGrantHistory(String userId) {
        PointAccount account = getAccount(userId);
        return pointGrantRepository.findByPointAccountIdOrderByCreatedAtDesc(account.getId());
    }

    @Transactional
    public PointGrant grant(String userId, String pointKey, long amount, Long requestedExpiryDays, GrantType grantType) {
        var existingGrant = pointGrantRepository.findByPointKey(pointKey);
        if (existingGrant.isPresent()) {
            PointGrant existing = existingGrant.get();
            if (existing.getUserId().equals(userId)
                    && existing.getOriginalAmount() == amount
                    && existing.getGrantType() == grantType) {
                return existing;
            }
            throw new PointException(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
        }

        long maxEarnOnce = policyProvider.getLongValue(ConfigKey.MAX_EARN_AMOUNT_ONCE);
        if (amount > maxEarnOnce) {
            throw new PointException(PointErrorCode.EXCEED_MAX_EARN_AMOUNT_ONCE);
        }

        PointAccount account = getOrCreateLockedAccount(userId);

        long maxHold = policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT);
        long currentUsableBalance = pointGrantRepository.sumUsableBalance(account.getId(), timeProvider.today());
        if (currentUsableBalance + amount > maxHold) {
            throw new PointException(PointErrorCode.EXCEED_MAX_HOLD_AMOUNT);
        }

        long expiryDays = requestedExpiryDays != null
                ? requestedExpiryDays
                : policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS);
        validateExpiryDays(expiryDays);
        LocalDate expiryDate = timeProvider.today().plusDays(expiryDays);

        PointGrant grant = PointGrant.builder()
                .pointKey(pointKey)
                .pointAccount(account)
                .userId(userId)
                .originalAmount(amount)
                .grantType(grantType)
                .expiryDate(expiryDate)
                .sourceUsageCancel(null)
                .build();
        pointGrantRepository.save(grant);

        account.increaseBalance(amount);

        return grant;
    }

    @Transactional
    public PointGrant cancel(String pointKey) {
        PointGrant grant = pointGrantRepository.findByPointKey(pointKey)
                .orElseThrow(() -> new PointException(PointErrorCode.GRANT_NOT_FOUND));

        if (grant.getStatus() == GrantStatus.CANCELLED) {
            throw new PointException(PointErrorCode.GRANT_ALREADY_CANCELLED);
        }
        if (grant.isFullyUsed()) {
            throw new PointException(PointErrorCode.GRANT_ALREADY_USED);
        }

        PointAccount account = pointAccountRepository.findByUserIdForUpdate(grant.getUserId())
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));
        account.decreaseBalance(grant.getRemainingAmount());
        grant.cancel();
        return grant;
    }

    private PointAccount getOrCreateLockedAccount(String userId) {
        return pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> pointAccountRepository.save(
                        PointAccount.builder().userId(userId).build()));
    }

    private void validateExpiryDays(long expiryDays) {
        if (expiryDays < 1 || expiryDays >= 1825) {
            throw new PointException(PointErrorCode.INVALID_EXPIRY_DAYS);
        }
    }
}
