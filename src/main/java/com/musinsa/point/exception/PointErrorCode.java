package com.musinsa.point.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode {

    // 계정
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 계정을 찾을 수 없습니다."),

    // 적립
    EXCEED_MAX_EARN_AMOUNT_ONCE(HttpStatus.UNPROCESSABLE_ENTITY, "1회 최대 적립 금액을 초과했습니다."),
    EXCEED_MAX_HOLD_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "최대 보유 포인트를 초과합니다."),
    DUPLICATE_POINT_KEY(HttpStatus.CONFLICT, "이미 처리된 포인트 키입니다."),

    // 사용
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "포인트 잔액이 부족합니다."),
    USAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 사용 내역을 찾을 수 없습니다."),

    // 취소
    USAGE_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 전액 취소된 사용 건입니다."),
    CANCEL_AMOUNT_EXCEEDS_REMAINING(HttpStatus.UNPROCESSABLE_ENTITY, "취소 금액이 취소 가능 금액을 초과합니다."),

    // 설정
    CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "설정 키를 찾을 수 없습니다."),

    // 공통
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
