package com.musinsa.point.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_usage_cancel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUsageCancel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pointKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_usage_id", nullable = false)
    private PointUsage pointUsage;

    @Column(nullable = false)
    private Long cancelAmount;

    @Builder
    public PointUsageCancel(String pointKey, PointUsage pointUsage, Long cancelAmount) {
        this.pointKey = pointKey;
        this.pointUsage = pointUsage;
        this.cancelAmount = cancelAmount;
    }
}
