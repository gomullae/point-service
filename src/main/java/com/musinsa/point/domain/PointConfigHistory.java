package com.musinsa.point.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_config_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointConfigHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_config_id", nullable = false)
    private PointConfig pointConfig;

    @Column(nullable = false)
    private String configKey;

    private String oldValue;

    @Column(nullable = false)
    private String newValue;

    @Column(nullable = false)
    private String changedBy;

    @Builder
    public PointConfigHistory(PointConfig pointConfig, String configKey,
                               String oldValue, String newValue, String changedBy) {
        this.pointConfig = pointConfig;
        this.configKey = configKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
    }
}
