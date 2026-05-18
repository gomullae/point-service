package com.musinsa.point.repository;

import com.musinsa.point.domain.entity.PointAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    Optional<PointAccount> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PointAccount a WHERE a.userId = :userId")
    Optional<PointAccount> findByUserIdForUpdate(@Param("userId") String userId);
}
