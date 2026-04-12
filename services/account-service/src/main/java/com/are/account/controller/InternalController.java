package com.are.account.controller;

import com.are.account.dto.DebitRequest;
import com.are.account.dto.InternalAccountResponse;
import com.are.account.service.InternalAccountService;
import com.are.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoints for inter-service communication.
 * These bypass JWT auth (matched as /internal/** in gateway config).
 * NOT exposed to external clients via the gateway.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final InternalAccountService internalAccountService;

    public InternalController(InternalAccountService internalAccountService) {
        this.internalAccountService = internalAccountService;
    }

    @GetMapping("/account/{userId}")
    public ResponseEntity<ApiResponse<InternalAccountResponse>> getAccountByUserId(
            @PathVariable Long userId) {
        InternalAccountResponse response = internalAccountService.getAccountByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/debit")
    public ResponseEntity<ApiResponse<InternalAccountResponse>> debit(
            @Valid @RequestBody DebitRequest request) {
        InternalAccountResponse response = internalAccountService.debit(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
