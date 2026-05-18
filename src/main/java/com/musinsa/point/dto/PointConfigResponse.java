package com.musinsa.point.dto;

import com.musinsa.point.domain.PointConfig;

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
