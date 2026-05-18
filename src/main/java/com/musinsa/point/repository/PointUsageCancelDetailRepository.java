package com.musinsa.point.repository;

import com.musinsa.point.domain.PointUsageCancelDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointUsageCancelDetailRepository extends JpaRepository<PointUsageCancelDetail, Long> {

    List<PointUsageCancelDetail> findByPointUsageCancelId(Long pointUsageCancelId);

    @Query("SELECT COALESCE(SUM(d.cancelAmount), 0) FROM PointUsageCancelDetail d WHERE d.pointUsageDetail.id = :usageDetailId")
    Long sumCancelAmountByUsageDetailId(@Param("usageDetailId") Long usageDetailId);
}
