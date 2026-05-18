package com.musinsa.point.domain.entity;

import com.musinsa.point.domain.enums.UsageStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pointKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_account_id", nullable = false)
    private PointAccount pointAccount;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private Long usedAmount;

    @Column(nullable = false)
    private Long cancelledAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsageStatus status;

    @Builder
    public PointUsage(String pointKey, PointAccount pointAccount, String userId,
                      String orderId, Long usedAmount) {
        this.pointKey = pointKey;
        this.pointAccount = pointAccount;
        this.userId = userId;
        this.orderId = orderId;
        this.usedAmount = usedAmount;
        this.cancelledAmount = 0L;
        this.status = UsageStatus.ACTIVE;
    }

    public void cancel(long cancelAmount) {
        this.cancelledAmount += cancelAmount;
        if (this.cancelledAmount.equals(this.usedAmount)) {
            this.status = UsageStatus.CANCELLED;
        } else {
            this.status = UsageStatus.PARTIAL_CANCELLED;
        }
    }

    public long getRemainCancelableAmount() {
        return this.usedAmount - this.cancelledAmount;
    }
}
