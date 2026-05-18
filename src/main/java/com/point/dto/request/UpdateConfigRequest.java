package com.point.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateConfigRequest(
        @NotBlank String newValue,
        @NotBlank String changedBy
) {}
