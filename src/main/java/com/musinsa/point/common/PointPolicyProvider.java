package com.musinsa.point.common;

import com.musinsa.point.domain.ConfigKey;
import com.musinsa.point.domain.PointConfig;
import com.musinsa.point.exception.PointErrorCode;
import com.musinsa.point.exception.PointException;
import com.musinsa.point.repository.PointConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointPolicyProvider {

    private final PointConfigRepository pointConfigRepository;

    @Cacheable(value = CacheConfig.POINT_CONFIG_CACHE, key = "#configKey.name()")
    public long getLongValue(ConfigKey configKey) {
        PointConfig config = pointConfigRepository.findByConfigKey(configKey.name())
                .orElseThrow(() -> new PointException(PointErrorCode.CONFIG_NOT_FOUND));
        return Long.parseLong(config.getConfigValue());
    }

    @CacheEvict(value = CacheConfig.POINT_CONFIG_CACHE, key = "#configKey.name()")
    public void evict(ConfigKey configKey) {
    }
}
