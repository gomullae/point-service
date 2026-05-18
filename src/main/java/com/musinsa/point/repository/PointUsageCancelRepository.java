package com.musinsa.point.repository;

import com.musinsa.point.domain.entity.PointUsageCancel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointUsageCancelRepository extends JpaRepository<PointUsageCancel, Long> {

    Optional<PointUsageCancel> findByPointKey(String pointKey);

    boolean existsByPointKey(String pointKey);
}
