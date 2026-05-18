package com.musinsa.point.controller;

import com.musinsa.point.domain.ConfigKey;
import com.musinsa.point.domain.GrantType;
import com.musinsa.point.dto.*;
import com.musinsa.point.service.PointConfigService;
import com.musinsa.point.service.PointGrantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PointGrantService pointGrantService;
    private final PointConfigService pointConfigService;

    @PostMapping("/points/earnings")
    public ResponseEntity<EarnPointResponse> manualGrant(@Valid @RequestBody EarnPointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EarnPointResponse.from(
                        pointGrantService.grant(request.userId(), request.pointKey(), request.amount(), GrantType.MANUAL)));
    }

    @GetMapping("/configs")
    public ResponseEntity<List<PointConfigResponse>> getAllConfigs() {
        List<PointConfigResponse> configs = pointConfigService.getAllConfigs().stream()
                .map(PointConfigResponse::from)
                .toList();
        return ResponseEntity.ok(configs);
    }

    @PutMapping("/configs/{configKey}")
    public ResponseEntity<PointConfigResponse> updateConfig(
            @PathVariable String configKey,
            @Valid @RequestBody UpdateConfigRequest request) {
        ConfigKey key = ConfigKey.valueOf(configKey);
        return ResponseEntity.ok(PointConfigResponse.from(
                pointConfigService.updateConfig(key, request.newValue(), request.changedBy())));
    }
}
