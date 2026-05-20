package com.point.repository;

import com.point.domain.entity.PointUsageDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {

    List<PointUsageDetail> findByPointUsageIdOrderByUseSequenceDesc(Long pointUsageId);
}
