package com.point.controller;

import com.point.domain.entity.PointConfig;
import com.point.domain.entity.PointGrant;
import com.point.domain.enums.ConfigKey;
import com.point.domain.enums.GrantType;
import com.point.service.PointConfigService;
import com.point.service.PointGrantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointGrantService pointGrantService;
    @MockitoBean
    private PointConfigService pointConfigService;

    private static final PointGrant STUB_MANUAL_GRANT = PointGrant.builder()
            .pointKey("manual-1")
            .pointAccount(null)
            .userId("admin-user")
            .originalAmount(5_000L)
            .grantType(GrantType.MANUAL)
            .expiryDate(LocalDate.of(2027, 5, 19))
            .sourceUsageCancel(null)
            .build();

    private static final PointConfig STUB_CONFIG = PointConfig.builder()
            .configKey(ConfigKey.MAX_GRANT_AMOUNT_ONCE.name())
            .configValue("200000")
            .valueType("INT")
            .description("1회 최대 적립 포인트")
            .build();

    @Test
    @DisplayName("수기 지급 성공 시 201을 반환하고 grantType이 MANUAL이다")
    void manualGrant_returns201() throws Exception {
        given(pointGrantService.grant(anyString(), anyString(), anyLong(), any(), any()))
                .willReturn(STUB_MANUAL_GRANT);

        mockMvc.perform(post("/api/v1/admin/points/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"admin-user","pointKey":"manual-1","amount":5000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grantType").value("MANUAL"));
    }

    @Test
    @DisplayName("설정 전체 조회 성공 시 200을 반환한다")
    void getAllConfigs_returns200() throws Exception {
        given(pointConfigService.getAllConfigs()).willReturn(List.of(STUB_CONFIG));

        mockMvc.perform(get("/api/v1/admin/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].configKey").value("MAX_GRANT_AMOUNT_ONCE"))
                .andExpect(jsonPath("$[0].configValue").value("200000"));
    }

    @Test
    @DisplayName("설정 변경 성공 시 200을 반환한다")
    void updateConfig_returns200() throws Exception {
        given(pointConfigService.updateConfig(any(), anyString(), anyString()))
                .willReturn(STUB_CONFIG);

        mockMvc.perform(put("/api/v1/admin/configs/MAX_GRANT_AMOUNT_ONCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newValue":"200000","changedBy":"admin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configValue").value("200000"));
    }

    @Test
    @DisplayName("존재하지 않는 configKey로 설정 변경 시 400을 반환한다")
    void updateConfig_invalidKey_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/configs/UNKNOWN_KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newValue":"100","changedBy":"admin"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
