package com.musinsa.point.domain.enums;

public enum GrantStatus {
    ACTIVE,     // 사용 가능
    CANCELLED   // 적립 취소됨 (만료 판단은 status가 아닌 expiry_date 기준)
}
