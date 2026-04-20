package com.are.account.service;

import com.are.account.dto.AccountResponse;
import com.are.account.dto.BalanceUpdateRequest;
import com.are.account.dto.CreateAccountRequest;
import com.are.common.exception.ForbiddenException;
import com.are.common.model.AccountEntity;
import com.are.common.model.AccountStatus;
import com.are.common.model.TransactionEntity;
import com.are.common.model.TransactionEntry;
import com.are.account.repository.AccountRepository;
import com.are.account.repository.TransactionRepository;
import com.are.common.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public AccountResponse getAccount(Long id) {
        AccountEntity account = findAccountOrThrow(id);
        checkAccess(account);
        return toResponse(account);
    }

    public AccountResponse getMyAccount() {
        Long userId = UserContext.getUserId();
        return accountRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ForbiddenException("No account found for the current user"));
    }

    public Page<AccountResponse> listAccounts(AccountStatus status, Pageable pageable) {
        if (!UserContext.isAdmin()) {
            throw new ForbiddenException("Only administrators can list all accounts");
        }
        if (status != null) {
            return accountRepository.findByStatus(status, pageable).map(this::toResponse);
        }
        return accountRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public AccountResponse updateAccount(Long id, CreateAccountRequest request) {
        AccountEntity account = findAccountOrThrow(id);
        checkAccess(account);

        if (request.ownerName() != null)
            account.setAccountName(request.ownerName());
        // if (request.email() != null)
        // account.setEmail(request.email());
        // if (request.phoneNumber() != null)
        // account.setPhoneNumber(request.phoneNumber());

        account = accountRepository.save(account);
        log.info("Account {} updated", id);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse closeAccount(Long id) {
        AccountEntity account = findAccountOrThrow(id);
        checkAccess(account);
        account.setStatus(AccountStatus.CLOSED);
        account = accountRepository.save(account);
        log.info("Account {} closed", id);
        return toResponse(account);
    }

    @Transactional
    public AccountResponse updateBalance(Long id, BalanceUpdateRequest request) {
        AccountEntity account = findAccountOrThrow(id);
        checkAccess(account);

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active: " + id);
        }

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal newBalance = balanceBefore.add(request.amount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient balance for account: " + id);
        }

        account.setBalance(newBalance);
        account = accountRepository.save(account);

        // Record the transaction
        TransactionEntry type = request.amount().compareTo(BigDecimal.ZERO) >= 0
                ? TransactionEntry.credit
                : TransactionEntry.debit;

        recordTransaction(account, type, request.amount().abs(),
                balanceBefore, newBalance, "Balance update");

        log.info("Account {} balance updated: {} -> {}", id, balanceBefore, newBalance);
        return toResponse(account);
    }

    // ----- Private helpers -----

    private AccountEntity findAccountOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
    }

    private void checkAccess(AccountEntity account) {
        Long currentUserId = UserContext.getUserId();
        if (UserContext.isAdmin()) {
            return; // Admins can access everything
        }

        if (account.getUser() == null || !account.getUser().getId().equals(currentUserId)) {
            log.warn("Access denied for user {} to account {}", currentUserId, account.getId());
            throw new ForbiddenException("You do not have permission to access this account");
        }
    }

    private void recordTransaction(AccountEntity account, TransactionEntry type, BigDecimal amount,
            BigDecimal balanceBefore, BigDecimal balanceAfter, String description) {
        TransactionEntity txn = new TransactionEntity();
        txn.setAccount(account);
        txn.setUser(account.getUser());
        txn.setType(type);
        txn.setAmount(amount);
        txn.setBalanceBefore(balanceBefore);
        txn.setBalanceAfter(balanceAfter);
        txn.setDescription(description);
        txn.setCorrelationId(MDC.get("correlationId"));
        txn.setReference("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        transactionRepository.save(txn);
    }

    private AccountResponse toResponse(AccountEntity a) {
        return new AccountResponse(
                a.getId(),
                a.getAccountNumber(),
                a.getAccountName(),
                a.getBalance(),
                a.getCurrency(),
                a.getStatus(),
                a.getUser() != null ? a.getUser().getEmail() : null,
                a.getUser() != null ? a.getUser().getPhoneNumber() : null,
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    String generateAccountNumber() {
        return "ARE" + String.valueOf(System.currentTimeMillis() % 1_000_000_000L);
    }
}
