package com.musinsa.point.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EarnPointRequest(
        @NotBlank String userId,
        @NotBlank String pointKey,
        @NotNull @Positive Long amount
) {}
