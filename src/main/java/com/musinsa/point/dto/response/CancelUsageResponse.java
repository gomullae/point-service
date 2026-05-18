package com.musinsa.point.dto.response;

import com.musinsa.point.domain.entity.PointUsageCancel;

import java.time.LocalDateTime;

public record CancelUsageResponse(
        String pointKey,
        Long cancelAmount,
        LocalDateTime createdAt
) {
    public static CancelUsageResponse from(PointUsageCancel cancel) {
        return new CancelUsageResponse(
                cancel.getPointKey(),
                cancel.getCancelAmount(),
                cancel.getCreatedAt()
        );
    }
}
