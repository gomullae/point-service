package com.musinsa.point.domain;

public enum GrantType {
    AUTO,           // 일반 적립
    MANUAL,         // 관리자 수기 지급
    CANCEL_RESTORE  // 사용취소로 인한 재적립 (만료된 경우)
}
