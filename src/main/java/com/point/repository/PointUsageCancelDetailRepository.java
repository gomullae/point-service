package com.point.repository;

import com.point.domain.entity.PointUsageCancelDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointUsageCancelDetailRepository extends JpaRepository<PointUsageCancelDetail, Long> {

    List<PointUsageCancelDetail> findByPointUsageCancelId(Long pointUsageCancelId);

    @Query("SELECT COALESCE(SUM(d.cancelAmount), 0) FROM PointUsageCancelDetail d WHERE d.pointUsageDetail.id = :usageDetailId")
    Long sumCancelAmountByUsageDetailId(@Param("usageDetailId") Long usageDetailId);

    @Query("""
            SELECT d.pointUsageDetail.id AS usageDetailId, COALESCE(SUM(d.cancelAmount), 0) AS cancelledAmount
            FROM PointUsageCancelDetail d
            WHERE d.pointUsageDetail.id IN :usageDetailIds
            GROUP BY d.pointUsageDetail.id
            """)
    List<CancelledAmountView> sumCancelAmountByUsageDetailIds(@Param("usageDetailIds") List<Long> usageDetailIds);

    interface CancelledAmountView {
        Long getUsageDetailId();
        Long getCancelledAmount();
    }
}
