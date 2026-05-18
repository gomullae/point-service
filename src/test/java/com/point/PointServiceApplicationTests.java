package com.point;

import com.point.common.TimeProvider;
import com.point.domain.entity.PointGrant;
import com.point.domain.entity.PointUsage;
import com.point.domain.enums.GrantStatus;
import com.point.domain.enums.GrantType;
import com.point.exception.PointErrorCode;
import com.point.exception.PointException;
import com.point.repository.*;
import com.point.service.PointGrantService;
import com.point.service.PointUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class PointServiceApplicationTests {

    @Autowired
    private PointGrantService pointGrantService;
    @Autowired
    private PointUsageService pointUsageService;
    @Autowired
    private PointGrantRepository pointGrantRepository;
    @Autowired
    private PointUsageRepository pointUsageRepository;
    @Autowired
    private PointUsageDetailRepository pointUsageDetailRepository;
    @Autowired
    private PointUsageCancelRepository pointUsageCancelRepository;
    @Autowired
    private PointUsageCancelDetailRepository pointUsageCancelDetailRepository;
    @Autowired
    private PointAccountRepository pointAccountRepository;

    @MockitoBean
    private TimeProvider timeProvider;

    @BeforeEach
    void setUp() {
        pointUsageCancelDetailRepository.deleteAll();
        pointUsageCancelRepository.deleteAll();
        pointUsageDetailRepository.deleteAll();
        pointUsageRepository.deleteAll();
        pointGrantRepository.deleteAll();
        pointAccountRepository.deleteAll();
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 5, 18));
    }

    @Test
    void manualGrantIsUsedBeforeAutoGrant() {
        pointGrantService.grant("u1", "auto-1", 1000, 30L, GrantType.AUTO);
        pointGrantService.grant("u1", "manual-1", 500, 365L, GrantType.MANUAL);

        PointUsage usage = pointUsageService.use("u1", "order-1", "use-1", 700);

        var details = pointUsageDetailRepository.findByPointUsageIdOrderByUseSequenceAsc(usage.getId());
        assertThat(details).hasSize(2);
        assertThat(details.get(0).getPointGrant().getPointKey()).isEqualTo("manual-1");
        assertThat(details.get(0).getUsedAmount()).isEqualTo(500);
        assertThat(details.get(1).getPointGrant().getPointKey()).isEqualTo("auto-1");
        assertThat(details.get(1).getUsedAmount()).isEqualTo(200);
    }

    @Test
    void expiredBalanceIsNotUsable() {
        pointGrantService.grant("u1", "earn-1", 1000, 1L, GrantType.AUTO);
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 5, 20));

        assertThat(pointGrantService.getUsableBalance("u1")).isZero();
        assertThatThrownBy(() -> pointUsageService.use("u1", "order-1", "use-1", 1))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    void unusedGrantCanBeCancelledButUsedGrantCannot() {
        PointGrant cancelled = pointGrantService.grant("u1", "earn-1", 1000, 30L, GrantType.AUTO);
        pointGrantService.cancel("earn-1");

        assertThat(cancelled.getStatus()).isEqualTo(GrantStatus.CANCELLED);
        assertThat(pointGrantService.getUsableBalance("u1")).isZero();

        pointGrantService.grant("u1", "earn-2", 1000, 30L, GrantType.AUTO);
        pointUsageService.use("u1", "order-1", "use-1", 100);

        assertThatThrownBy(() -> pointGrantService.cancel("earn-2"))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.GRANT_ALREADY_USED);
    }

    @Test
    void duplicatePointKeyReturnsExistingResultOnlyForSameRequest() {
        PointGrant first = pointGrantService.grant("u1", "earn-1", 1000, 30L, GrantType.AUTO);
        PointGrant retried = pointGrantService.grant("u1", "earn-1", 1000, 30L, GrantType.AUTO);

        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> pointGrantService.grant("u1", "earn-1", 2000, 30L, GrantType.AUTO))
                .isInstanceOf(PointException.class)
                .extracting(e -> ((PointException) e).getErrorCode())
                .isEqualTo(PointErrorCode.DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST);
    }

    @Test
    void usageCancellationRestoresExpiredGrantAsNewGrant() {
        pointGrantService.grant("u1", "earn-1", 1000, 1L, GrantType.AUTO);
        pointGrantService.grant("u1", "earn-2", 500, 30L, GrantType.AUTO);
        PointUsage usage = pointUsageService.use("u1", "order-1", "use-1", 1200);

        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 5, 20));
        pointUsageService.cancel(usage.getPointKey(), 1100);

        List<PointGrant> grants = pointGrantRepository.findByPointAccountIdOrderByCreatedAtDesc(
                pointAccountRepository.findByUserId("u1").orElseThrow().getId()
        );
        assertThat(grants.stream().filter(g -> g.getGrantType() == GrantType.CANCEL_RESTORE).count()).isEqualTo(1);
        assertThat(pointGrantService.getUsableBalance("u1")).isEqualTo(1400);
    }
}
