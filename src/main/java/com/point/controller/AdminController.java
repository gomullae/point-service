package com.point.controller;

import com.point.domain.enums.ConfigKey;
import com.point.domain.enums.GrantType;
import com.point.dto.request.*;
import com.point.dto.response.*;
import com.point.service.PointConfigService;
import com.point.service.PointGrantService;
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
                        pointGrantService.grant(request.userId(), request.pointKey(), request.amount(), request.expiryDays(), GrantType.MANUAL)));
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
        ConfigKey key = ConfigKey.from(configKey);
        return ResponseEntity.ok(PointConfigResponse.from(
                pointConfigService.updateConfig(key, request.newValue(), request.changedBy())));
    }
}
