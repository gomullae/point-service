package com.musinsa.point.dto;

import com.musinsa.point.domain.PointAccount;

public record PointBalanceResponse(String userId, Long balance) {

    public static PointBalanceResponse from(PointAccount account) {
        return new PointBalanceResponse(account.getUserId(), account.getBalance());
    }
}
