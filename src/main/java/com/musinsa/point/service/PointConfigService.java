package com.musinsa.point.service;

import com.musinsa.point.common.PointPolicyProvider;
import com.musinsa.point.domain.ConfigKey;
import com.musinsa.point.domain.PointConfig;
import com.musinsa.point.domain.PointConfigHistory;
import com.musinsa.point.exception.PointErrorCode;
import com.musinsa.point.exception.PointException;
import com.musinsa.point.repository.PointConfigHistoryRepository;
import com.musinsa.point.repository.PointConfigRepository;
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
}
