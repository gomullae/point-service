package com.point.dto.response;

import com.point.domain.entity.PointGrant;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record GrantPointResponse(
        String pointKey,
        String userId,
        Long originalAmount,
        Long remainingAmount,
        String grantType,
        LocalDate expiryDate,
        LocalDateTime createdAt
) {
    public static GrantPointResponse from(PointGrant grant) {
        return new GrantPointResponse(
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
