package com.musinsa.point.controller;

import com.musinsa.point.domain.GrantType;
import com.musinsa.point.dto.*;
import com.musinsa.point.service.PointGrantService;
import com.musinsa.point.service.PointUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointGrantService pointGrantService;
    private final PointUsageService pointUsageService;

    @PostMapping("/earnings")
    public ResponseEntity<EarnPointResponse> earn(@Valid @RequestBody EarnPointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EarnPointResponse.from(
                        pointGrantService.grant(request.userId(), request.pointKey(), request.amount(), GrantType.AUTO)));
    }

    @PostMapping("/usages")
    public ResponseEntity<UsePointResponse> use(@Valid @RequestBody UsePointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UsePointResponse.from(
                        pointUsageService.use(request.userId(), request.orderId(), request.pointKey(), request.amount())));
    }

    @PostMapping("/usages/{pointKey}/cancel")
    public ResponseEntity<CancelUsageResponse> cancel(
            @PathVariable String pointKey,
            @Valid @RequestBody CancelUsageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CancelUsageResponse.from(
                        pointUsageService.cancel(pointKey, request.cancelAmount())));
    }

    @GetMapping("/{userId}/balance")
    public ResponseEntity<PointBalanceResponse> getBalance(@PathVariable String userId) {
        return ResponseEntity.ok(PointBalanceResponse.from(pointGrantService.getAccount(userId)));
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<PointGrantHistoryResponse>> getHistory(@PathVariable String userId) {
        List<PointGrantHistoryResponse> history = pointGrantService.getGrantHistory(userId).stream()
                .map(PointGrantHistoryResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}
