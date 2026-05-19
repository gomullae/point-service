package com.point.controller;

import com.point.common.PointPolicyProvider;
import com.point.common.TimeProvider;
import com.point.domain.enums.ConfigKey;
import com.point.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointPolicyProvider policyProvider;
    @MockitoBean
    private TimeProvider timeProvider;

    @BeforeEach
    void setUp() {
        given(policyProvider.getLongValue(ConfigKey.MAX_GRANT_AMOUNT_ONCE)).willReturn(100_000L);
        given(policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT)).willReturn(1_000_000L);
        given(policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS)).willReturn(365L);
        given(timeProvider.today()).willReturn(LocalDate.of(2026, 5, 20));
    }

    @Test
    @DisplayName("적립 API는 성공 시 201과 응답 필드를 반환한다")
    void earn_success() throws Exception {
        mockMvc.perform(post("/api/v1/points/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-api",
                                  "pointKey": "earn-api-1",
                                  "amount": 1000,
                                  "expiryDays": 30
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pointKey").value("earn-api-1"))
                .andExpect(jsonPath("$.userId").value("user-api"))
                .andExpect(jsonPath("$.originalAmount").value(1000))
                .andExpect(jsonPath("$.remainingAmount").value(1000))
                .andExpect(jsonPath("$.grantType").value("AUTO"))
                .andExpect(jsonPath("$.expiryDate").value("2026-06-19"));
    }

    @Test
    @DisplayName("필수 요청값이 없으면 400을 반환한다")
    void earn_validationError() throws Exception {
        mockMvc.perform(post("/api/v1/points/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-api",
                                  "amount": 1000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("pointKey")));
    }

    @Test
    @DisplayName("잔액 부족 시 사용 API는 422를 반환한다")
    void use_insufficientBalance() throws Exception {
        mockMvc.perform(post("/api/v1/points/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-api",
                                  "pointKey": "earn-api-1",
                                  "amount": 1000
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/points/usages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-api",
                                  "orderId": "order-api-1",
                                  "pointKey": "use-api-1",
                                  "amount": 1001
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
    }

    @Test
    @DisplayName("잔액 조회 API는 사용 가능한 잔액을 반환한다")
    void getBalance_success() throws Exception {
        mockMvc.perform(post("/api/v1/points/earnings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "user-api",
                                  "pointKey": "earn-api-1",
                                  "amount": 1000
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/points/user-api/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-api"))
                .andExpect(jsonPath("$.balance").value(1000));
    }
}
