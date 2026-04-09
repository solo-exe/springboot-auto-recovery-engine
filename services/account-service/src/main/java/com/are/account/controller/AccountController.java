package com.are.account.controller;

import com.are.account.dto.AccountResponse;
import com.are.account.dto.BalanceUpdateRequest;
import com.are.account.dto.CreateAccountRequest;
import com.are.common.model.AccountStatus;
import com.are.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @GetMapping
    public ResponseEntity<Page<AccountResponse>> listAccounts(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                accountService.listAccounts(status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable Long id, @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AccountResponse> closeAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.closeAccount(id));
    }

    @PutMapping("/{id}/balance")
    public ResponseEntity<AccountResponse> updateBalance(
            @PathVariable Long id, @Valid @RequestBody BalanceUpdateRequest request) {
        return ResponseEntity.ok(accountService.updateBalance(id, request));
    }
}
