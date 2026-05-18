package com.musinsa.point.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String configKey;

    @Column(nullable = false)
    private String configValue;

    @Column(nullable = false)
    private String valueType;

    private String description;

    @Builder
    public PointConfig(String configKey, String configValue, String valueType, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.valueType = valueType;
        this.description = description;
    }

    public void updateValue(String newValue) {
        this.configValue = newValue;
    }
}
