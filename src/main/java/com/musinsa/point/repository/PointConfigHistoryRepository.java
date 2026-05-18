package com.musinsa.point.repository;

import com.musinsa.point.domain.PointConfigHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointConfigHistoryRepository extends JpaRepository<PointConfigHistory, Long> {

    List<PointConfigHistory> findByPointConfigIdOrderByCreatedAtDesc(Long pointConfigId);
}
