package com.point.service;

import com.point.domain.entity.PointAccount;
import com.point.repository.PointGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PointExpirationService {

    private final PointGrantRepository pointGrantRepository;

    public void expire(PointAccount account, LocalDate today) {
        long expiredAmount = pointGrantRepository.findExpiredGrants(account.getId(), today).stream()
                .mapToLong(grant -> grant.expireRemainingAmount())
                .sum();

        if (expiredAmount > 0) {
            account.decreaseBalance(expiredAmount);
        }
    }
}
