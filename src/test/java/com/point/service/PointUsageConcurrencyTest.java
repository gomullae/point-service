package com.point.service;

import com.point.policy.PointPolicyProvider;
import com.point.support.TimeProvider;
import com.point.domain.enums.ConfigKey;
import com.point.domain.enums.GrantType;
import com.point.exception.PointException;
import com.point.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PointUsageConcurrencyTest {

    @Autowired
    private PointGrantService pointGrantService;
    @Autowired
    private PointUsageService pointUsageService;
    @Autowired
    private PointUsageRepository pointUsageRepository;
    @Autowired
    private PointUsageCancelDetailRepository pointUsageCancelDetailRepository;
    @Autowired
    private PointUsageCancelRepository pointUsageCancelRepository;
    @Autowired
    private PointUsageDetailRepository pointUsageDetailRepository;
    @Autowired
    private PointGrantRepository pointGrantRepository;
    @Autowired
    private PointAccountRepository pointAccountRepository;

    @MockitoBean
    private PointPolicyProvider policyProvider;
    @MockitoBean
    private TimeProvider timeProvider;

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 20);
    private static final String USER = "concurrent-user";

    @BeforeEach
    void setUp() {
        cleanDomainTables();

        given(policyProvider.getLongValue(ConfigKey.MAX_GRANT_AMOUNT_ONCE)).willReturn(100_000L);
        given(policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT)).willReturn(1_000_000L);
        given(policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS)).willReturn(365L);
        given(timeProvider.today()).willReturn(TODAY);
    }

    @AfterEach
    void tearDown() {
        cleanDomainTables();
    }

    private void cleanDomainTables() {
        pointUsageCancelDetailRepository.deleteAll();
        pointUsageCancelRepository.deleteAll();
        pointUsageDetailRepository.deleteAll();
        pointUsageRepository.deleteAll();
        pointGrantRepository.deleteAll();
        pointAccountRepository.deleteAll();
    }

    @Test
    @DisplayName("동시에 사용 요청이 들어와도 잔액을 초과해서 차감하지 않는다")
    void concurrentUse_doesNotOverspendBalance() throws Exception {
        // given
        pointGrantService.grant(USER, "grant-concurrent-1", 1_000L, null, GrantType.AUTO);

        // when
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        // then
        Callable<Result> use800 = () -> useAfterStart(ready, start, "order-800", "use-800", 800L);
        Callable<Result> use700 = () -> useAfterStart(ready, start, "order-700", "use-700", 700L);

        var future1 = executor.submit(use800);
        var future2 = executor.submit(use700);
        ready.await();
        start.countDown();

        List<Result> results = List.of(future1.get(), future2.get());
        executor.shutdown();

        long successCount = results.stream().filter(Result::success).count();
        long failureCount = results.stream().filter(result -> !result.success()).count();
        long totalUsed = pointUsageRepository.findAll().stream()
                .filter(usage -> usage.getUserId().equals(USER))
                .mapToLong(usage -> usage.getUsedAmount() - usage.getCancelledAmount())
                .sum();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);
        assertThat(totalUsed).isLessThanOrEqualTo(1_000L);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_000L - totalUsed);
    }

    @Test
    @DisplayName("신규 사용자에게 동시에 적립해도 계정은 하나만 생성되고 잔액이 합산된다")
    void concurrentGrant_createsSingleAccount() throws Exception {
        // given
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        // when
        Callable<Result> grant500 = () -> grantAfterStart(ready, start, "grant-concurrent-new-500", 500L);
        Callable<Result> grant700 = () -> grantAfterStart(ready, start, "grant-concurrent-new-700", 700L);

        // then
        var future1 = executor.submit(grant500);
        var future2 = executor.submit(grant700);
        ready.await();
        start.countDown();

        List<Result> results = List.of(future1.get(), future2.get());
        executor.shutdown();

        assertThat(results).allMatch(Result::success);
        assertThat(pointAccountRepository.findAll())
                .filteredOn(account -> account.getUserId().equals(USER))
                .hasSize(1);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_200L);
}

    private Result useAfterStart(CountDownLatch ready, CountDownLatch start,
                                 String orderId, String pointKey, long amount) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            pointUsageService.use(USER, orderId, pointKey, amount);
            return new Result(true, null);
        } catch (PointException e) {
            return new Result(false, e);
        }
    }

    private Result grantAfterStart(CountDownLatch ready, CountDownLatch start,
                                   String pointKey, long amount) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            pointGrantService.grant(USER, pointKey, amount, null, GrantType.AUTO);
            return new Result(true, null);
        } catch (Exception e) {
            return new Result(false, e);
        }
    }

    private record Result(boolean success, Exception exception) {
    }
}
