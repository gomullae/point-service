package com.point.domain.enums;

public enum RestoreType {
    RESTORE_TO_ORIGINAL, // 기존 적립 건 remaining_amount 복원
    CREATE_NEW_GRANT     // 만료된 적립 건 → 신규 point_grant 생성
}
