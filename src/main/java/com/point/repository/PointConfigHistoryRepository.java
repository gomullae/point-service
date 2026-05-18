package com.point.repository;

import com.point.domain.entity.PointConfigHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointConfigHistoryRepository extends JpaRepository<PointConfigHistory, Long> {

    List<PointConfigHistory> findByPointConfigIdOrderByCreatedAtDesc(Long pointConfigId);
}
