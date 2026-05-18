package com.point.dto.response;

import com.point.domain.entity.PointGrant;

import java.time.LocalDateTime;

public record CancelGrantResponse(
        String pointKey,
        String userId,
        String status,
        LocalDateTime updatedAt
) {
    public static CancelGrantResponse from(PointGrant grant) {
        return new CancelGrantResponse(
                grant.getPointKey(),
                grant.getUserId(),
                grant.getStatus().name(),
                grant.getUpdatedAt()
        );
    }
}
