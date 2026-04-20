package com.are.account.service;

import com.are.account.dto.DebitRequest;
import com.are.account.dto.InternalAccountResponse;
import com.are.account.repository.AccountRepository;
import com.are.account.repository.TransactionRepository;
import com.are.common.exception.InsufficientFundsException;
import com.are.common.exception.ResourceNotFoundException;
import com.are.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Internal account operations used by other microservices (payment-service).
 */
@Service
public class InternalAccountService {

    private static final Logger log = LoggerFactory.getLogger(InternalAccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public InternalAccountService(AccountRepository accountRepository,
                                   TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public InternalAccountResponse getAccountByUserId(Long userId) {
        AccountEntity account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + userId));
        return toResponse(account);
    }

    @Transactional
    public InternalAccountResponse debit(DebitRequest request) {
        AccountEntity account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + request.accountId()));

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal newBalance = balanceBefore.subtract(request.amount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + balanceBefore + ", requested: " + request.amount());
        }

        account.setBalance(newBalance);
        account = accountRepository.save(account);

        // Record transaction
        TransactionEntity txn = new TransactionEntity();
        txn.setAccount(account);
        txn.setUser(account.getUser());
        txn.setType(TransactionEntry.debit);
        txn.setStatus(TransactionStatus.success);
        txn.setAmount(request.amount());
        txn.setBalanceBefore(balanceBefore);
        txn.setBalanceAfter(newBalance);
        txn.setReference(request.paymentReference());
        txn.setDescription("Payment debit");
        txn.setCorrelationId(MDC.get("correlationId") != null ? MDC.get("correlationId") : "internal");
        transactionRepository.save(txn);

        log.info("Debited {} from account {}, new balance: {}", request.amount(), account.getId(), newBalance);
        return toResponse(account);
    }

    private InternalAccountResponse toResponse(AccountEntity a) {
        return new InternalAccountResponse(
                a.getId(),
                a.getAccountNumber(),
                a.getBalance(),
                a.getStatus(),
                a.getUser() != null ? a.getUser().getEmail() : null
        );
    }
}
