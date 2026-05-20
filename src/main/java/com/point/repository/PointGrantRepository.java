package com.point.repository;

import com.point.domain.entity.PointGrant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PointGrantRepository extends JpaRepository<PointGrant, Long> {

    Optional<PointGrant> findByPointKey(String pointKey);

    boolean existsByPointKey(String pointKey);

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
                                      @Param("today") LocalDate today,
                                      Pageable pageable);

    List<PointGrant> findByPointAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query("""
            SELECT g FROM PointGrant g
            WHERE g.pointAccount.id = :accountId
              AND g.status = 'ACTIVE'
              AND g.remainingAmount > 0
              AND g.expiryDate < :today
            """)
    List<PointGrant> findExpiredGrants(@Param("accountId") Long accountId,
                                       @Param("today") LocalDate today);
}
