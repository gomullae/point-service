package com.point;

import com.point.common.PointPolicyProvider;
import com.point.common.TimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class PointServiceApplicationTests {

    @MockitoBean
    private PointPolicyProvider policyProvider;
    @MockitoBean
    private TimeProvider timeProvider;

    @Test
    void contextLoads() {
    }
}
