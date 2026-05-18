package com.musinsa.point.service;

import com.musinsa.point.common.PointKeyGenerator;
import com.musinsa.point.common.PointPolicyProvider;
import com.musinsa.point.common.TimeProvider;
import com.musinsa.point.domain.entity.*;
import com.musinsa.point.domain.enums.*;
import com.musinsa.point.exception.PointErrorCode;
import com.musinsa.point.exception.PointException;
import com.musinsa.point.repository.PointAccountRepository;
import com.musinsa.point.repository.PointGrantRepository;
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
    public List<PointGrant> getGrantHistory(String userId) {
        PointAccount account = getAccount(userId);
        return pointGrantRepository.findByPointAccountIdOrderByCreatedAtDesc(account.getId());
    }

    @Transactional
    public PointGrant grant(String userId, String pointKey, long amount, GrantType grantType) {
        if (pointGrantRepository.existsByPointKey(pointKey)) {
            throw new PointException(PointErrorCode.DUPLICATE_POINT_KEY);
        }

        long maxEarnOnce = policyProvider.getLongValue(ConfigKey.MAX_EARN_AMOUNT_ONCE);
        if (amount > maxEarnOnce) {
            throw new PointException(PointErrorCode.EXCEED_MAX_EARN_AMOUNT_ONCE);
        }

        PointAccount account = getOrCreateLockedAccount(userId);

        long maxHold = policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT);
        if (account.getBalance() + amount > maxHold) {
            throw new PointException(PointErrorCode.EXCEED_MAX_HOLD_AMOUNT);
        }

        long expiryDays = policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS);
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

    private PointAccount getOrCreateLockedAccount(String userId) {
        return pointAccountRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> pointAccountRepository.save(
                        PointAccount.builder().userId(userId).build()));
    }
}
