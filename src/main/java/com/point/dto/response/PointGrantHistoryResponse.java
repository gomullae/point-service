package com.point.dto.response;

import com.point.domain.entity.PointGrant;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PointGrantHistoryResponse(
        String pointKey,
        Long originalAmount,
        Long remainingAmount,
        String grantType,
        String status,
        LocalDate expiryDate,
        LocalDateTime createdAt
) {
    public static PointGrantHistoryResponse from(PointGrant grant) {
        return new PointGrantHistoryResponse(
                grant.getPointKey(),
                grant.getOriginalAmount(),
                grant.getRemainingAmount(),
                grant.getGrantType().name(),
                grant.getStatus().name(),
                grant.getExpiryDate(),
                grant.getCreatedAt()
        );
    }
}
