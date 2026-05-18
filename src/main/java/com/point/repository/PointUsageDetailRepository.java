package com.point.repository;

import com.point.domain.entity.PointUsageDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {

    // use_sequence ASC: 취소 복원 시 먼저 차감된 순서대로 복원
    List<PointUsageDetail> findByPointUsageIdOrderByUseSequenceAsc(Long pointUsageId);
}
