package com.musinsa.point.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CancelUsageRequest(
        @NotNull @Positive Long cancelAmount
) {}
