package com.are.account.controller;

import com.are.account.dto.AccountResponse;
import com.are.account.dto.BalanceUpdateRequest;
import com.are.account.dto.CreateAccountRequest;
import com.are.common.model.AccountStatus;
import com.are.common.dto.ApiResponse;
import com.are.account.service.AccountService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        log.info("Received request to create account for user email: {}", request.email());
        AccountResponse response = accountService.createAccount(request);
        log.info("Successfully created account with ID: {}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable Long id) {
        log.debug("Fetching account with ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccount(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AccountResponse>>> listAccounts(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("Listing accounts (status: {}, page: {}, size: {})", status, page, size);
        Page<AccountResponse> response = accountService.listAccounts(status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable Long id, @Valid @RequestBody CreateAccountRequest request) {
        log.info("Updating account ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(accountService.updateAccount(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(@PathVariable Long id) {
        log.info("Closing account ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success(accountService.closeAccount(id)));
    }

    @PutMapping("/{id}/balance")
    public ResponseEntity<ApiResponse<AccountResponse>> updateBalance(
            @PathVariable Long id, @Valid @RequestBody BalanceUpdateRequest request) {
        log.info("Updating balance for account ID: {} with request: {}", id, request);
        return ResponseEntity.ok(ApiResponse.success(accountService.updateBalance(id, request)));
    }
}
