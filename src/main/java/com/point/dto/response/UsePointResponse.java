package com.point.dto.response;

import com.point.domain.entity.PointUsage;

import java.time.LocalDateTime;

public record UsePointResponse(
        String pointKey,
        String userId,
        String orderId,
        Long usedAmount,
        String status,
        LocalDateTime createdAt
) {
    public static UsePointResponse from(PointUsage usage) {
        return new UsePointResponse(
                usage.getPointKey(),
                usage.getUserId(),
                usage.getOrderId(),
                usage.getUsedAmount(),
                usage.getStatus().name(),
                usage.getCreatedAt()
        );
    }
}
