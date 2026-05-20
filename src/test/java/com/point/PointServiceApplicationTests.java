package com.point;

import com.point.policy.PointPolicyProvider;
import com.point.support.TimeProvider;
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
