package com.point.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode {

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 계정을 찾을 수 없습니다."),

    EXCEED_MAX_GRANT_AMOUNT_ONCE(HttpStatus.UNPROCESSABLE_ENTITY, "1회 최대 적립 금액을 초과했습니다."),
    EXCEED_MAX_HOLD_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "최대 보유 포인트를 초과합니다."),
    DUPLICATE_POINT_KEY(HttpStatus.CONFLICT, "이미 처리된 포인트 키입니다."),
    DUPLICATE_POINT_KEY_WITH_DIFFERENT_REQUEST(HttpStatus.CONFLICT, "동일한 포인트 키가 다른 요청에 이미 사용되었습니다."),
    GRANT_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 적립 내역을 찾을 수 없습니다."),
    GRANT_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 적립 건입니다."),
    GRANT_ALREADY_USED(HttpStatus.UNPROCESSABLE_ENTITY, "이미 일부 사용된 적립 건은 취소할 수 없습니다."),
    INVALID_EXPIRY_DAYS(HttpStatus.UNPROCESSABLE_ENTITY, "만료일은 1일 이상 5년 미만이어야 합니다."),

    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "포인트 잔액이 부족합니다."),
    USAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "포인트 사용 내역을 찾을 수 없습니다."),

    USAGE_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 전액 취소된 사용 건입니다."),
    CANCEL_AMOUNT_EXCEEDS_REMAINING(HttpStatus.UNPROCESSABLE_ENTITY, "취소 금액이 취소 가능 금액을 초과합니다."),

    CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "설정 키를 찾을 수 없습니다."),
    INVALID_CONFIG_VALUE(HttpStatus.UNPROCESSABLE_ENTITY, "유효하지 않은 설정값입니다."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
