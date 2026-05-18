package com.musinsa.point.dto.response;

import com.musinsa.point.domain.entity.PointConfig;

public record PointConfigResponse(
        String configKey,
        String configValue,
        String valueType,
        String description
) {
    public static PointConfigResponse from(PointConfig config) {
        return new PointConfigResponse(
                config.getConfigKey(),
                config.getConfigValue(),
                config.getValueType(),
                config.getDescription()
        );
    }
}
