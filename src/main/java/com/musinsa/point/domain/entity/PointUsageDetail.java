package com.musinsa.point.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_usage_detail")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_usage_id", nullable = false)
    private PointUsage pointUsage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_grant_id", nullable = false)
    private PointGrant pointGrant;

    // 동일 사용 건 내 적립 차감 순서 (1부터 시작). 취소 복원 순서의 기준.
    @Column(nullable = false)
    private Integer useSequence;

    @Column(nullable = false)
    private Long usedAmount;

    @Builder
    public PointUsageDetail(PointUsage pointUsage, PointGrant pointGrant,
                            Integer useSequence, Long usedAmount) {
        this.pointUsage = pointUsage;
        this.pointGrant = pointGrant;
        this.useSequence = useSequence;
        this.usedAmount = usedAmount;
    }
}
