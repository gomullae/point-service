package com.point.dto.response;

public record PointBalanceResponse(String userId, Long balance) {

    public static PointBalanceResponse of(String userId, Long balance) {
        return new PointBalanceResponse(userId, balance);
    }
}
