package com.musinsa.point.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateConfigRequest(
        @NotBlank String newValue,
        @NotBlank String changedBy
) {}
