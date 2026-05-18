package com.musinsa.point.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "point_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private Long balance;

    @Version
    private Long version;

    @Builder
    public PointAccount(String userId) {
        this.userId = userId;
        this.balance = 0L;
    }

    public void increaseBalance(long amount) {
        this.balance += amount;
    }

    public void decreaseBalance(long amount) {
        this.balance -= amount;
    }
}
