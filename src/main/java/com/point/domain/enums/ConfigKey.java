package com.point.domain.enums;

import com.point.exception.PointErrorCode;
import com.point.exception.PointException;

public enum ConfigKey {
    MAX_GRANT_AMOUNT_ONCE {
        @Override
        public void validate(long value) {
            validatePositive(value);
        }
    },
    MAX_HOLD_AMOUNT {
        @Override
        public void validate(long value) {
            validatePositive(value);
        }
    },
    DEFAULT_EXPIRY_DAYS {
        @Override
        public void validate(long value) {
            validatePositive(value);
            if (value >= MAX_EXPIRY_DAYS_EXCLUSIVE) {
                throw new PointException(PointErrorCode.INVALID_CONFIG_VALUE);
            }
        }
    };

    private static final long MAX_EXPIRY_DAYS_EXCLUSIVE = 1825L;

    public String value() {
        return this.name();
    }

    public abstract void validate(long value);

    public static ConfigKey from(String value) {
        try {
            return ConfigKey.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new PointException(PointErrorCode.CONFIG_NOT_FOUND);
        }
    }

    private static void validatePositive(long value) {
        if (value <= 0) {
            throw new PointException(PointErrorCode.INVALID_CONFIG_VALUE);
        }
    }
}
