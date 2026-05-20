package com.point.service;

import com.point.common.PointPolicyProvider;
import com.point.domain.entity.PointConfig;
import com.point.domain.entity.PointConfigHistory;
import com.point.domain.enums.ConfigKey;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.PointConfigHistoryRepository;
import com.point.repository.PointConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointConfigService {

    private final PointConfigRepository pointConfigRepository;
    private final PointConfigHistoryRepository pointConfigHistoryRepository;
    private final PointPolicyProvider pointPolicyProvider;

    @Transactional(readOnly = true)
    public List<PointConfig> getAllConfigs() {
        return pointConfigRepository.findAll();
    }

    @Transactional
    public PointConfig updateConfig(ConfigKey configKey, String newValue, String changedBy) {
        validate(configKey, newValue);

        PointConfig config = pointConfigRepository.findByConfigKey(configKey.name())
                .orElseThrow(() -> new PointException(PointErrorCode.CONFIG_NOT_FOUND));

        String oldValue = config.getConfigValue();
        config.updateValue(newValue);

        pointConfigHistoryRepository.save(PointConfigHistory.builder()
                .pointConfig(config)
                .configKey(configKey.name())
                .oldValue(oldValue)
                .newValue(newValue)
                .changedBy(changedBy)
                .build());

        pointPolicyProvider.evict(configKey);

        return config;
    }

    private void validate(ConfigKey configKey, String newValue) {
        final long value;
        try {
            value = Long.parseLong(newValue);
        } catch (NumberFormatException e) {
            throw new PointException(PointErrorCode.INVALID_CONFIG_VALUE);
        }

        configKey.validate(value);
    }
}
