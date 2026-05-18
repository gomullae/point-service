package com.point.repository;

import com.point.domain.entity.PointConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointConfigRepository extends JpaRepository<PointConfig, Long> {

    Optional<PointConfig> findByConfigKey(String configKey);
}
