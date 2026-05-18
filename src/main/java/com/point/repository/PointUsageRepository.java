package com.point.repository;

import com.point.domain.entity.PointUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointUsageRepository extends JpaRepository<PointUsage, Long> {

    Optional<PointUsage> findByPointKey(String pointKey);

    boolean existsByPointKey(String pointKey);
}
