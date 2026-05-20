package com.point.service;

import com.point.common.PointPolicyProvider;
import com.point.common.TimeProvider;
import com.point.domain.entity.*;
import com.point.domain.enums.*;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.PointAccountRepository;
import com.point.repository.PointGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
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
    private final PointExpirationService pointExpirationService;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public PointAccount getAccount(String userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));
    }

    @Transactional
    public long getUsableBalance(String userId) {
        PointAccount account = getAccount(userId);
        pointExpirationService.expire(account, timeProvider.today());
        return account.getBalance();
    }

    @Transactional(readOnly = true)
    public List<PointGrant> getGrantHistory(String userId) {
        PointAccount account = getAccount(userId);
        return pointGrantRepository.findByPointAccountIdOrderByCreatedAtDesc(account.getId());
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class)
    @Transactional
    public PointGrant grant(String userId, String pointKey, long amount, Long requestedExpiryDays, GrantType grantType) {
        long expiryDays = requestedExpiryDays != null
                ? requestedExpiryDays
                : policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS);
        validateExpiryDays(expiryDays);
        LocalDate expiryDate = timeProvider.today().plusDays(expiryDays);

        var existingGrant = pointGrantRepository.findByPointKey(pointKey);
        if (existingGrant.isPresent()) {
            PointGrant existing = existingGrant.get();
            if (isSameGrantRequest(existing, userId, amount, grantType, expiryDate)) {
                return existing;
            }
            throw new PointException(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
        }

        long maxGrantOnce = policyProvider.getLongValue(ConfigKey.MAX_GRANT_AMOUNT_ONCE);
        if (amount > maxGrantOnce) {
            throw new PointException(PointErrorCode.EXCEED_MAX_GRANT_AMOUNT_ONCE);
        }

        PointAccount account = getOrCreateAccount(userId);
        pointExpirationService.expire(account, timeProvider.today());

        long maxHold = policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT);
        if (account.getBalance() + amount > maxHold) {
            throw new PointException(PointErrorCode.EXCEED_MAX_HOLD_AMOUNT);
        }

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

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class)
    @Transactional
    public PointGrant cancel(String pointKey) {
        PointGrant grant = pointGrantRepository.findByPointKey(pointKey)
                .orElseThrow(() -> new PointException(PointErrorCode.GRANT_NOT_FOUND));

        if (grant.getStatus() == GrantStatus.CANCELLED) {
            throw new PointException(PointErrorCode.GRANT_ALREADY_CANCELLED);
        }
        if (grant.hasBeenUsed()) {
            throw new PointException(PointErrorCode.GRANT_ALREADY_USED);
        }

        PointAccount account = pointAccountRepository.findByUserId(grant.getUserId())
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));
        account.decreaseBalance(grant.getRemainingAmount());
        grant.cancel();
        return grant;
    }

    private PointAccount getOrCreateAccount(String userId) {
        return pointAccountRepository.findByUserId(userId)
                .orElseGet(() -> pointAccountRepository.save(
                        PointAccount.builder().userId(userId).build()));
    }

    private void validateExpiryDays(long expiryDays) {
        if (expiryDays < 1 || expiryDays >= 1825) {
            throw new PointException(PointErrorCode.INVALID_EXPIRY_DAYS);
        }
    }

    private boolean isSameGrantRequest(PointGrant existing, String userId, long amount,
                                       GrantType grantType, LocalDate expiryDate) {
        return existing.getUserId().equals(userId)
                && existing.getOriginalAmount() == amount
                && existing.getGrantType() == grantType
                && existing.getExpiryDate().equals(expiryDate);
    }
}
