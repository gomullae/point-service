package com.point.repository;

import com.point.domain.entity.PointGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PointGrantRepository extends JpaRepository<PointGrant, Long> {

    Optional<PointGrant> findByPointKey(String pointKey);

    boolean existsByPointKey(String pointKey);

    /**
     * 사용 가능한 적립금 조회: ACTIVE 상태이고 만료되지 않은 건만
     * 만료 기준: expiry_date >= today (expiry_date < today 이면 만료)
     * 정렬: 수기 지급 우선, 이후 만료일 오름차순, 동일 만료일은 생성순(id ASC)
     */
    @Query("""
            SELECT g FROM PointGrant g
            WHERE g.pointAccount.id = :accountId
              AND g.status = 'ACTIVE'
              AND g.remainingAmount > 0
              AND g.expiryDate >= :today
            ORDER BY
              CASE WHEN g.grantType = 'MANUAL' THEN 0 ELSE 1 END,
              g.expiryDate ASC,
              g.id ASC
            """)
    List<PointGrant> findUsableGrants(@Param("accountId") Long accountId,
                                      @Param("today") LocalDate today);

    List<PointGrant> findByPointAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("""
            SELECT COALESCE(SUM(g.remainingAmount), 0)
            FROM PointGrant g
            WHERE g.pointAccount.id = :accountId
              AND g.status = 'ACTIVE'
              AND g.remainingAmount > 0
              AND g.expiryDate >= :today
            """)
    Long sumUsableBalance(@Param("accountId") Long accountId,
                          @Param("today") LocalDate today);
}
