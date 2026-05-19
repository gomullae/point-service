package com.point.domain.entity;

import com.point.domain.enums.GrantStatus;
import com.point.domain.enums.GrantType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "point_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointGrant extends BaseEntity {

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
    private Long originalAmount;

    @Column(nullable = false)
    private Long remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrantType grantType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrantStatus status;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_usage_cancel_id")
    private PointUsageCancel sourceUsageCancel;

    @Builder
    public PointGrant(String pointKey, PointAccount pointAccount, String userId,
                      Long originalAmount, GrantType grantType,
                      LocalDate expiryDate, PointUsageCancel sourceUsageCancel) {
        this.pointKey = pointKey;
        this.pointAccount = pointAccount;
        this.userId = userId;
        this.originalAmount = originalAmount;
        this.remainingAmount = originalAmount;
        this.grantType = grantType;
        this.status = GrantStatus.ACTIVE;
        this.expiryDate = expiryDate;
        this.sourceUsageCancel = sourceUsageCancel;
    }

    public void useAmount(long amount) {
        this.remainingAmount -= amount;
    }

    public void restoreAmount(long amount) {
        this.remainingAmount += amount;
    }

    public void cancel() {
        this.status = GrantStatus.CANCELLED;
        this.remainingAmount = 0L;
    }

    public boolean isExpired(LocalDate today) {
        return this.expiryDate.isBefore(today);
    }

    public boolean hasBeenUsed() {
        return this.remainingAmount < this.originalAmount;
    }
}
