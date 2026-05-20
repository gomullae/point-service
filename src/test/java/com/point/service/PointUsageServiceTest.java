package com.point.service;

import com.point.policy.PointPolicyProvider;
import com.point.support.TimeProvider;
import com.point.domain.entity.PointGrant;
import com.point.domain.entity.PointUsage;
import com.point.domain.entity.PointUsageCancel;
import com.point.domain.enums.ConfigKey;
import com.point.domain.enums.GrantType;
import com.point.domain.enums.RestoreType;
import com.point.domain.enums.UsageStatus;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.PointUsageCancelDetailRepository;
import com.point.repository.PointUsageDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Transactional
class PointUsageServiceTest {

    @Autowired
    private PointGrantService pointGrantService;
    @Autowired
    private PointUsageService pointUsageService;
    @Autowired
    private PointUsageDetailRepository pointUsageDetailRepository;
    @Autowired
    private PointUsageCancelDetailRepository pointUsageCancelDetailRepository;

    @MockitoBean
    private PointPolicyProvider policyProvider;
    @MockitoBean
    private TimeProvider timeProvider;

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 19);
    private static final String USER = "user1";

    @BeforeEach
    void setUp() {
        given(policyProvider.getLongValue(ConfigKey.MAX_GRANT_AMOUNT_ONCE)).willReturn(100_000L);
        given(policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT)).willReturn(1_000_000L);
        given(policyProvider.getLongValue(ConfigKey.DEFAULT_EXPIRY_DAYS)).willReturn(365L);
        given(timeProvider.today()).willReturn(TODAY);
    }

    private PointGrant grant(String key, long amount, GrantType type) {
        return pointGrantService.grant(USER, key, amount, null, type);
    }

    private PointGrant grant(String key, long amount) {
        return grant(key, amount, GrantType.AUTO);
    }

    private PointUsage use(String orderId, String key, long amount) {
        return pointUsageService.use(USER, orderId, key, amount);
    }

    @Test
    @DisplayName("정상 사용 시 잔액이 감소한다")
    void use_success() {
        // given
        grant("grant-1", 1_000L);

        // when
        PointUsage usage = use("order-1", "use-1", 700L);

        // then
        assertThat(usage.getUsedAmount()).isEqualTo(700L);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(300L);
    }

    @Test
    @DisplayName("동일 사용 요청을 재시도하면 기존 결과를 반환한다")
    void use_idempotent() {
        // given
        grant("grant-1", 1_000L);

        // when
        PointUsage first = use("order-1", "use-1", 500L);
        PointUsage retried = use("order-1", "use-1", 500L);

        // then
        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(500L);
    }

    @Test
    @DisplayName("같은 key로 다른 사용 요청이 오면 예외를 던진다")
    void use_duplicateKeyDifferentRequest() {
        // given
        grant("grant-1", 1_000L);
        use("order-1", "use-1", 500L);

        // when & then
        assertThatThrownBy(() -> use("order-2", "use-1", 500L))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
    }

    @Test
    @DisplayName("잔액이 부족하면 예외를 던진다")
    void use_insufficientBalance() {
        // given
        grant("grant-1", 1_000L);

        // when & then
        assertThatThrownBy(() -> use("order-1", "use-1", 1_001L))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("포인트 계정이 없으면 예외를 던진다")
    void use_accountNotFound() {
        // when & then
        assertThatThrownBy(() -> pointUsageService.use("no-such-user", "order-1", "use-1", 100L))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("MANUAL 적립 포인트를 AUTO보다 먼저 사용한다")
    void use_manualGrantUsedFirst() {
        // given
        pointGrantService.grant(USER, "auto-1", 1_000L, 30L, GrantType.AUTO);
        pointGrantService.grant(USER, "manual-1", 500L, 365L, GrantType.MANUAL);

        // when
        PointUsage usage = use("order-1", "use-1", 700L);

        // then
        var details = pointUsageDetailRepository.findByPointUsageIdOrderByUseSequenceDesc(usage.getId());
        assertThat(details).hasSize(2);
        assertThat(details.get(0).getPointGrant().getPointKey()).isEqualTo("auto-1");
        assertThat(details.get(0).getUsedAmount()).isEqualTo(200L);
        assertThat(details.get(1).getPointGrant().getPointKey()).isEqualTo("manual-1");
        assertThat(details.get(1).getUsedAmount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("만료일이 빠른 포인트를 먼저 사용한다")
    void use_earlierExpiryUsedFirst() {
        // given
        pointGrantService.grant(USER, "near-1", 500L, 30L, GrantType.AUTO);
        pointGrantService.grant(USER, "far-1", 500L, 365L, GrantType.AUTO);

        // when
        PointUsage usage = use("order-1", "use-1", 600L);

        // then
        var details = pointUsageDetailRepository.findByPointUsageIdOrderByUseSequenceDesc(usage.getId());
        assertThat(details).hasSize(2);
        assertThat(details.get(0).getPointGrant().getPointKey()).isEqualTo("far-1");
        assertThat(details.get(0).getUsedAmount()).isEqualTo(100L);
        assertThat(details.get(1).getPointGrant().getPointKey()).isEqualTo("near-1");
        assertThat(details.get(1).getUsedAmount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("사용 취소 시 원본 적립에 금액이 복원된다")
    void cancelUsage_success() {
        // given
        PointGrant g = grant("grant-1", 1_000L);
        PointUsage usage = use("order-1", "use-1", 1_000L);

        // when
        pointUsageService.cancel(usage.getPointKey(), 1_000L);

        // then
        assertThat(g.getRemainingAmount()).isEqualTo(1_000L);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("만료된 적립에서 사용한 포인트 취소 시 신규 적립이 생성된다")
    void cancelUsage_expiredGrant_createNewGrant() {
        // given
        pointGrantService.grant(USER, "grant-1", 1_000L, 1L, GrantType.AUTO);
        PointUsage usage = use("order-1", "use-1", 1_000L);

        // when
        given(timeProvider.today()).willReturn(TODAY.plusDays(2));

        // then
        PointUsageCancel cancel = pointUsageService.cancel(usage.getPointKey(), 1_000L);

        var cancelDetails = pointUsageCancelDetailRepository.findByPointUsageCancelId(cancel.getId());
        assertThat(cancelDetails).hasSize(1);
        assertThat(cancelDetails.get(0).getRestoreType()).isEqualTo(RestoreType.CREATE_NEW_GRANT);
        assertThat(cancelDetails.get(0).getRestoredPointGrant()).isNotNull();
        assertThat(cancelDetails.get(0).getRestoredPointGrant().getGrantType()).isEqualTo(GrantType.CANCEL_RESTORE);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("과제 예시: 만료 적립분은 신규 적립으로, 미만료 적립분은 원본에 복원된다")
    void assignmentScenario_cancelUsage_mixedExpiredAndActiveRestore() {
        // given
        pointGrantService.grant(USER, "A", 1_000L, 1L, GrantType.AUTO);
        pointGrantService.grant(USER, "B", 500L, 30L, GrantType.AUTO);
        PointUsage usage = use("A1234", "C", 1_200L);

        // when
        given(timeProvider.today()).willReturn(TODAY.plusDays(2));
        PointUsageCancel cancel = pointUsageService.cancel(usage.getPointKey(), 1_100L);

        // then
        var cancelDetails = pointUsageCancelDetailRepository.findByPointUsageCancelId(cancel.getId());
        assertThat(cancelDetails).hasSize(2);
        // LIFO: sequence 2(B, 미만료) 먼저 복원
        assertThat(cancelDetails.get(0).getCancelAmount()).isEqualTo(200L);
        assertThat(cancelDetails.get(0).getRestoreType()).isEqualTo(RestoreType.RESTORE_TO_ORIGINAL);
        assertThat(cancelDetails.get(0).getRestoredPointGrant()).isNull();
        // sequence 1(A, 만료) 신규 적립
        assertThat(cancelDetails.get(1).getCancelAmount()).isEqualTo(900L);
        assertThat(cancelDetails.get(1).getRestoreType()).isEqualTo(RestoreType.CREATE_NEW_GRANT);
        assertThat(cancelDetails.get(1).getRestoredPointGrant()).isNotNull();
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_400L);
        assertThat(usage.getRemainCancelableAmount()).isEqualTo(100L);
    }

    @Test
    @DisplayName("전액 취소된 사용을 다시 취소하면 예외를 던진다")
    void cancelUsage_alreadyCancelled() {
        // given
        grant("grant-1", 1_000L);
        PointUsage usage = use("order-1", "use-1", 1_000L);
        pointUsageService.cancel(usage.getPointKey(), 1_000L);

        // when & then
        assertThatThrownBy(() -> pointUsageService.cancel(usage.getPointKey(), 1_000L))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.USAGE_ALREADY_CANCELLED);
    }

    @Test
    @DisplayName("취소 금액이 취소 가능 금액을 초과하면 예외를 던진다")
    void cancelUsage_exceedsRemaining() {
        // given
        grant("grant-1", 1_000L);
        PointUsage usage = use("order-1", "use-1", 1_000L);

        // when & then
        assertThatThrownBy(() -> pointUsageService.cancel(usage.getPointKey(), 1_001L))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.CANCEL_AMOUNT_EXCEEDS_REMAINING);
    }

    @Test
    @DisplayName("부분 취소 후 사용 상태가 PARTIAL_CANCELLED로 변경된다")
    void cancelUsage_partial() {
        // given
        grant("grant-1", 1_000L);
        PointUsage usage = use("order-1", "use-1", 1_000L);

        // when
        pointUsageService.cancel(usage.getPointKey(), 400L);

        // then
        assertThat(usage.getRemainCancelableAmount()).isEqualTo(600L);
        assertThat(usage.getStatus()).isEqualTo(UsageStatus.PARTIAL_CANCELLED);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(400L);
    }

    @Test
    @DisplayName("부분 취소를 여러 번 반복할 수 있다")
    void cancelUsage_partialTwice() {
        // given
        grant("grant-1", 1_000L);
        PointUsage usage = use("order-1", "use-1", 1_000L);

        // when
        pointUsageService.cancel(usage.getPointKey(), 400L);
        pointUsageService.cancel(usage.getPointKey(), 300L);

        // then
        assertThat(usage.getRemainCancelableAmount()).isEqualTo(300L);
        assertThat(usage.getStatus()).isEqualTo(UsageStatus.PARTIAL_CANCELLED);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(700L);
    }

    @Test
    @DisplayName("여러 적립에서 차감된 사용을 취소하면 사용 순서대로 복원된다")
    void cancelUsage_multipleGrants() {
        // given
        PointGrant g1 = grant("grant-1", 1_000L);
        PointGrant g2 = grant("grant-2", 500L);
        PointUsage usage = use("order-1", "use-1", 1_200L);

        // when
        pointUsageService.cancel(usage.getPointKey(), 1_200L);

        // then
        assertThat(g1.getRemainingAmount()).isEqualTo(1_000L);
        assertThat(g2.getRemainingAmount()).isEqualTo(500L);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_500L);
    }

    @Test
    @DisplayName("존재하지 않는 사용키로 취소 시 예외를 던진다")
    void cancelUsage_usageNotFound() {
        // when & then
        assertThatThrownBy(() -> pointUsageService.cancel("non-existent-key", 100L))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.USAGE_NOT_FOUND);
    }
}
