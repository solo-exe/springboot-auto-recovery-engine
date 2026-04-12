package com.are.account.service;

import com.are.account.dto.FundRequest;
import com.are.account.repository.AccountRepository;
import com.are.account.repository.TransactionRepository;
import com.are.common.exception.ResourceNotFoundException;
import com.are.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Admin operations — account funding for test data seeding.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AdminService(AccountRepository accountRepository,
                        TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Map<String, Object> fundAccount(FundRequest request) {
        AccountEntity account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found: " + request.accountNumber()));

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal newBalance = balanceBefore.add(request.amount());
        account.setBalance(newBalance);
        account = accountRepository.save(account);

        // Record credit transaction
        TransactionEntity txn = new TransactionEntity();
        txn.setAccount(account);
        txn.setUser(account.getUser());
        txn.setType(TransactionEntry.credit);
        txn.setStatus(TransactionStatus.success);
        txn.setAmount(request.amount());
        txn.setBalanceBefore(balanceBefore);
        txn.setBalanceAfter(newBalance);
        txn.setReference("FUND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        txn.setDescription("Admin fund account");
        txn.setCorrelationId(MDC.get("correlationId") != null ? MDC.get("correlationId") : "admin");
        transactionRepository.save(txn);

        log.info("Admin funded account {} with {}, new balance: {}",
                request.accountNumber(), request.amount(), newBalance);

        return Map.of(
                "accountNumber", account.getAccountNumber(),
                "previousBalance", balanceBefore,
                "fundedAmount", request.amount(),
                "newBalance", newBalance
        );
    }
}
