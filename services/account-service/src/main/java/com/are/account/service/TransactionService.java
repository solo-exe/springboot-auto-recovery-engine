package com.are.account.service;

import com.are.common.model.TransactionEntity;
import com.are.account.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Page<TransactionEntity> getTransactions(
            Long accountId,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        if (from != null && to != null) {
            System.out.println("THIS RUNS ");
            return transactionRepository.findByAccountIdAndCreatedAtBetween(accountId, from, to, pageable);
        } else {
            System.out.println("THIS RUNS INSTEAD");
            return transactionRepository.findByAccountId(accountId, pageable);
        }
    }

    public Map<String, Object> getTransactionSummary(Long accountId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = transactionRepository.getTransactionSummary(accountId, from, to);

        Map<String, Object> summary = new HashMap<>();
        summary.put("accountId", accountId);
        summary.put("from", from);
        summary.put("to", to);

        for (Object[] row : results) {
            String type = row[0].toString();
            summary.put(type.toLowerCase() + "Count", row[1]);
            summary.put(type.toLowerCase() + "Amount", row[2]);
        }

        return summary;
    }
}
