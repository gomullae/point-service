package com.point.service;

import com.point.policy.PointPolicyProvider;
import com.point.support.TimeProvider;
import com.point.domain.entity.*;
import com.point.domain.enums.*;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.PointAccountRepository;
import com.point.repository.PointGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    @Transactional
    public List<PointGrant> getGrantHistory(String userId) {
        PointAccount account = getAccount(userId);
        pointExpirationService.expire(account, timeProvider.today());
        return pointGrantRepository.findByPointAccountIdOrderByCreatedAtDesc(account.getId());
    }

    @Retryable(retryFor = {
            ObjectOptimisticLockingFailureException.class,
            DataIntegrityViolationException.class
    })
    @Transactional
    public PointGrant grant(String userId, String pointKey, long amount, Long requestedExpiryDays, GrantType grantType) {
        Optional<PointGrant> idempotent = findExistingGrant(pointKey, userId, amount, grantType);
        if (idempotent.isPresent()) return idempotent.get();

        LocalDate expiryDate = resolveExpiryDate(requestedExpiryDays);

        validateGrantAmount(amount);

        PointAccount account = getOrCreateAccountAndExpire(userId);
        validateHoldLimit(account, amount);

        return saveGrant(account, userId, pointKey, amount, grantType, expiryDate);
    }

    private LocalDate resolveExpiryDate(Long requestedExpiryDays) {
        long expiryDays = requestedExpiryDays != null
                ? requestedExpiryDays
                : policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS);
        validateExpiryDays(expiryDays);
        return timeProvider.today().plusDays(expiryDays);
    }

    private Optional<PointGrant> findExistingGrant(String pointKey, String userId, long amount, GrantType grantType) {
        return pointGrantRepository.findByPointKey(pointKey).map(existing -> {
            if (isSameGrantRequest(existing, userId, amount, grantType)) {
                return existing;
            }
            throw new PointException(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
        });
    }

    private void validateGrantAmount(long amount) {
        long maxGrantOnce = policyProvider.getLongValue(ConfigKey.MAX_GRANT_AMOUNT_ONCE);
        if (amount > maxGrantOnce) {
            throw new PointException(PointErrorCode.EXCEED_MAX_GRANT_AMOUNT_ONCE);
        }
    }

    private PointAccount getOrCreateAccountAndExpire(String userId) {
        PointAccount account = getOrCreateAccount(userId);
        pointExpirationService.expire(account, timeProvider.today());
        return account;
    }

    private void validateHoldLimit(PointAccount account, long amount) {
        long maxHold = policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT);
        if (account.getBalance() + amount > maxHold) {
            throw new PointException(PointErrorCode.EXCEED_MAX_HOLD_AMOUNT);
        }
    }

    private PointGrant saveGrant(PointAccount account, String userId, String pointKey,
                                 long amount, GrantType grantType, LocalDate expiryDate) {
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
        if (grant.isExpired(timeProvider.today())) {
            throw new PointException(PointErrorCode.GRANT_ALREADY_EXPIRED);
        }
        if (grant.hasBeenUsed()) {
            throw new PointException(PointErrorCode.GRANT_ALREADY_USED);
        }

        PointAccount account = pointAccountRepository.findByUserId(grant.getUserId())
                .orElseThrow(() -> new PointException(PointErrorCode.ACCOUNT_NOT_FOUND));
        pointExpirationService.expire(account, timeProvider.today());

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

    private boolean isSameGrantRequest(PointGrant existing, String userId, long amount, GrantType grantType) {
        return existing.getUserId().equals(userId)
                && existing.getOriginalAmount() == amount
                && existing.getGrantType() == grantType;
    }
}
