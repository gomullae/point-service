package com.musinsa.point.dto.response;

import com.musinsa.point.domain.entity.PointGrant;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EarnPointResponse(
        String pointKey,
        String userId,
        Long originalAmount,
        Long remainingAmount,
        String grantType,
        LocalDate expiryDate,
        LocalDateTime createdAt
) {
    public static EarnPointResponse from(PointGrant grant) {
        return new EarnPointResponse(
                grant.getPointKey(),
                grant.getUserId(),
                grant.getOriginalAmount(),
                grant.getRemainingAmount(),
                grant.getGrantType().name(),
                grant.getExpiryDate(),
                grant.getCreatedAt()
        );
    }
}
