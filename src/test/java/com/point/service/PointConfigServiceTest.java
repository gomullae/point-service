package com.point.service;

import com.point.common.PointPolicyProvider;
import com.point.common.TimeProvider;
import com.point.domain.entity.PointConfig;
import com.point.domain.enums.ConfigKey;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.PointConfigHistoryRepository;
import com.point.repository.PointConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

@SpringBootTest
@Transactional
class PointConfigServiceTest {

    @Autowired
    private PointConfigService pointConfigService;
    @Autowired
    private PointConfigRepository pointConfigRepository;
    @Autowired
    private PointConfigHistoryRepository pointConfigHistoryRepository;

    @MockitoBean
    private PointPolicyProvider pointPolicyProvider;
    @MockitoBean
    private TimeProvider timeProvider;

    @Test
    @DisplayName("전체 설정을 조회한다")
    void getAllConfigs_success() {
        List<PointConfig> configs = pointConfigService.getAllConfigs();

        assertThat(configs).isNotEmpty();
        assertThat(configs).extracting(PointConfig::getConfigKey)
                .contains(
                        ConfigKey.MAX_GRANT_AMOUNT_ONCE.name(),
                        ConfigKey.MAX_HOLD_AMOUNT.name(),
                        ConfigKey.DEFAULT_EXPIRY_DAYS.name()
                );
    }

    @Test
    @DisplayName("설정 변경 시 값이 업데이트되고 이력이 저장된다")
    void updateConfig_success() {
        PointConfig config = pointConfigService.updateConfig(ConfigKey.MAX_GRANT_AMOUNT_ONCE, "200000", "admin");

        assertThat(config.getConfigValue()).isEqualTo("200000");

        var histories = pointConfigHistoryRepository.findByPointConfigIdOrderByCreatedAtDesc(config.getId());
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getNewValue()).isEqualTo("200000");
        assertThat(histories.get(0).getChangedBy()).isEqualTo("admin");

        then(pointPolicyProvider).should().evict(ConfigKey.MAX_GRANT_AMOUNT_ONCE);
    }

    @Test
    @DisplayName("숫자가 아닌 값으로 설정 변경 시 예외를 던진다")
    void updateConfig_invalidValue_notNumber() {
        assertThatThrownBy(() -> pointConfigService.updateConfig(ConfigKey.MAX_GRANT_AMOUNT_ONCE, "abc", "admin"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_CONFIG_VALUE);
    }

    @Test
    @DisplayName("0 이하 값으로 설정 변경 시 예외를 던진다")
    void updateConfig_invalidValue_zero() {
        assertThatThrownBy(() -> pointConfigService.updateConfig(ConfigKey.MAX_GRANT_AMOUNT_ONCE, "0", "admin"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_CONFIG_VALUE);
    }

    @Test
    @DisplayName("DEFAULT_EXPIRY_DAYS를 1825 이상으로 설정하면 예외를 던진다")
    void updateConfig_expiryDays_tooLarge() {
        assertThatThrownBy(() -> pointConfigService.updateConfig(ConfigKey.DEFAULT_EXPIRY_DAYS, "1825", "admin"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_CONFIG_VALUE);
    }
}
