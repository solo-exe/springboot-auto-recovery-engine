package com.are.account.controller;

import com.are.account.dto.FundRequest;
import com.are.account.dto.ProfileResponse;
import com.are.account.service.ProfileService;
import com.are.common.dto.ApiResponse;
import com.are.common.model.TransactionEntity;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Protected endpoints requiring JWT authentication.
 * User ID is extracted from the X-User-Id header forwarded by the gateway.
 */
@RestController
@RequestMapping("/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @RequestHeader("X-User-Id") Long userId) {
        ProfileResponse response = profileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<TransactionEntity>>> getTransactions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<TransactionEntity> transactions = profileService.getTransactions(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }
}
