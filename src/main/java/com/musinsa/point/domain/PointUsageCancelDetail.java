package com.musinsa.point.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_usage_cancel_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageCancelDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_usage_cancel_id", nullable = false)
    private PointUsageCancel pointUsageCancel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_usage_detail_id", nullable = false)
    private PointUsageDetail pointUsageDetail;

    @Column(nullable = false)
    private Long cancelAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RestoreType restoreType;

    // CREATE_NEW_GRANT 일 때만 세팅 (새로 생성된 point_grant 참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restored_point_grant_id")
    private PointGrant restoredPointGrant;

    @Builder
    public PointUsageCancelDetail(PointUsageCancel pointUsageCancel,
                                  PointUsageDetail pointUsageDetail,
                                  Long cancelAmount,
                                  RestoreType restoreType,
                                  PointGrant restoredPointGrant) {
        this.pointUsageCancel = pointUsageCancel;
        this.pointUsageDetail = pointUsageDetail;
        this.cancelAmount = cancelAmount;
        this.restoreType = restoreType;
        this.restoredPointGrant = restoredPointGrant;
    }
}
