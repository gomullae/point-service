package com.musinsa.point.domain;

public enum ConfigKey {
    MAX_EARN_AMOUNT_ONCE,
    MAX_HOLD_AMOUNT,
    DEFAULT_EXPIRY_DAYS;

    public String value() {
        return this.name();
    }
}
