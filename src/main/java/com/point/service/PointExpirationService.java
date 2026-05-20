package com.point.service;

import com.point.domain.entity.PointAccount;
import com.point.repository.PointGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PointExpirationService {

    private final PointGrantRepository pointGrantRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void expire(PointAccount account, LocalDate today) {
        long expiredAmount = pointGrantRepository.findExpiredGrants(account.getId(), today).stream()
                .mapToLong(grant -> grant.expireRemainingAmount())
                .sum();

        if (expiredAmount > 0) {
            account.decreaseBalance(expiredAmount);
        }
    }
}
