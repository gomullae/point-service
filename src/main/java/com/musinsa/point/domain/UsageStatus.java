package com.musinsa.point.domain;

public enum UsageStatus {
    ACTIVE,            // 사용 완료 (취소 없음)
    PARTIAL_CANCELLED, // 일부 취소됨
    CANCELLED          // 전액 취소됨
}
