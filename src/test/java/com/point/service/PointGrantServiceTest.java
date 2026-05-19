package com.point.service;

import com.point.common.PointPolicyProvider;
import com.point.common.TimeProvider;
import com.point.domain.entity.PointGrant;
import com.point.domain.enums.ConfigKey;
import com.point.domain.enums.GrantStatus;
import com.point.domain.enums.GrantType;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Transactional
class PointGrantServiceTest {

    @Autowired
    private PointGrantService pointGrantService;
    @Autowired
    private PointUsageService pointUsageService;

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

    private PointGrant grant(String key, long amount) {
        return pointGrantService.grant(USER, key, amount, null, GrantType.AUTO);
    }

    @Test
    @DisplayName("정상 적립 시 잔액이 증가한다")
    void grant_success() {
        PointGrant result = grant("key-1", 1_000L);

        assertThat(result.getOriginalAmount()).isEqualTo(1_000L);
        assertThat(result.getRemainingAmount()).isEqualTo(1_000L);
        assertThat(result.getGrantType()).isEqualTo(GrantType.AUTO);
        assertThat(result.getStatus()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("동일 요청을 재시도하면 기존 결과를 반환한다")
    void grant_idempotent() {
        PointGrant first = grant("key-1", 1_000L);
        PointGrant retried = grant("key-1", 1_000L);

        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(pointGrantService.getUsableBalance(USER)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("같은 key라도 만료일이 다른 요청이면 예외를 던진다")
    void grant_duplicateKeyDifferentExpiryDays() {
        pointGrantService.grant(USER, "key-1", 1_000L, 30L, GrantType.AUTO);

        assertThatThrownBy(() -> pointGrantService.grant(USER, "key-1", 1_000L, 60L, GrantType.AUTO))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
    }

    @Test
    @DisplayName("같은 key로 다른 요청이 오면 예외를 던진다")
    void grant_duplicateKeyDifferentRequest() {
        grant("key-1", 1_000L);

        assertThatThrownBy(() -> pointGrantService.grant(USER, "key-1", 2_000L, null, GrantType.AUTO))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
    }

    @Test
    @DisplayName("1회 최대 적립 한도를 초과하면 예외를 던진다")
    void grant_exceedMaxOnce() {
        assertThatThrownBy(() -> pointGrantService.grant(USER, "key-1", 100_001L, null, GrantType.AUTO))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.EXCEED_MAX_GRANT_AMOUNT_ONCE);
    }

    @Test
    @DisplayName("최대 보유 포인트를 초과하면 예외를 던진다")
    void grant_exceedMaxHold() {
        given(policyProvider.getLongValue(ConfigKey.MAX_HOLD_AMOUNT)).willReturn(500L);
        grant("key-1", 500L);

        assertThatThrownBy(() -> grant("key-2", 1L))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.EXCEED_MAX_HOLD_AMOUNT);
    }

    @Test
    @DisplayName("유효하지 않은 만료일(0일)이면 예외를 던진다")
    void grant_invalidExpiryDays() {
        assertThatThrownBy(() -> pointGrantService.grant(USER, "key-1", 1_000L, 0L, GrantType.AUTO))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_EXPIRY_DAYS);
    }

    @Test
    @DisplayName("사용되지 않은 적립을 취소하면 잔액이 감소한다")
    void cancel_success() {
        grant("key-1", 1_000L);

        PointGrant cancelled = pointGrantService.cancel("key-1");

        assertThat(cancelled.getStatus()).isEqualTo(GrantStatus.CANCELLED);
        assertThat(cancelled.getRemainingAmount()).isZero();
        assertThat(pointGrantService.getUsableBalance(USER)).isZero();
    }

    @Test
    @DisplayName("이미 취소된 적립을 다시 취소하면 예외를 던진다")
    void cancel_alreadyCancelled() {
        grant("key-1", 1_000L);
        pointGrantService.cancel("key-1");

        assertThatThrownBy(() -> pointGrantService.cancel("key-1"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.GRANT_ALREADY_CANCELLED);
    }

    @Test
    @DisplayName("일부 사용된 적립은 취소할 수 없다")
    void cancel_alreadyUsed() {
        grant("key-1", 1_000L);
        pointUsageService.use(USER, "order-1", "use-key-1", 500L);

        assertThatThrownBy(() -> pointGrantService.cancel("key-1"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.GRANT_ALREADY_USED);
    }

    @Test
    @DisplayName("만료된 포인트는 잔액 조회에서 제외된다")
    void getUsableBalance_excludesExpired() {
        pointGrantService.grant(USER, "key-1", 1_000L, 1L, GrantType.AUTO);
        given(timeProvider.today()).willReturn(TODAY.plusDays(2));

        assertThat(pointGrantService.getUsableBalance(USER)).isZero();
    }

    @Test
    @DisplayName("적립 이력 조회 시 모든 적립 내역이 반환된다")
    void getGrantHistory_returnsAll() {
        grant("key-1", 1_000L);
        grant("key-2", 2_000L);

        List<PointGrant> history = pointGrantService.getGrantHistory(USER);

        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("커스텀 만료일로 적립 시 요청한 만료일이 적용된다")
    void grant_withCustomExpiryDays() {
        PointGrant result = pointGrantService.grant(USER, "key-1", 1_000L, 30L, GrantType.AUTO);

        assertThat(result.getExpiryDate()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    @DisplayName("만료일 미입력 시 기본 만료일이 적용된다")
    void grant_withoutExpiryDays_usesDefaultExpiryDays() {
        PointGrant result = pointGrantService.grant(USER, "key-1", 1_000L, null, GrantType.AUTO);

        assertThat(result.getExpiryDate()).isEqualTo(TODAY.plusDays(365));
    }

    @Test
    @DisplayName("만료일 최대 허용값(1824일)으로 적립할 수 있다")
    void grant_expiryDays_maxAllowed() {
        PointGrant result = pointGrantService.grant(USER, "key-1", 1_000L, 1824L, GrantType.AUTO);

        assertThat(result.getExpiryDate()).isEqualTo(TODAY.plusDays(1824));
    }

    @Test
    @DisplayName("만료일 상한(1825일)을 초과하면 예외를 던진다")
    void grant_expiryDays_tooLarge() {
        assertThatThrownBy(() -> pointGrantService.grant(USER, "key-1", 1_000L, 1825L, GrantType.AUTO))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INVALID_EXPIRY_DAYS);
    }

    @Test
    @DisplayName("존재하지 않는 포인트키로 적립 취소 시 예외를 던진다")
    void cancel_grantNotFound() {
        assertThatThrownBy(() -> pointGrantService.cancel("non-existent-key"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.GRANT_NOT_FOUND);
    }

    @Test
    @DisplayName("포인트 계정이 없으면 잔액 조회 시 예외를 던진다")
    void getUsableBalance_accountNotFound() {
        assertThatThrownBy(() -> pointGrantService.getUsableBalance("no-such-user"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.ACCOUNT_NOT_FOUND);
    }
}
