package com.are.account.repository;

import com.are.common.model.TransactionEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

        Page<TransactionEntity> findByAccountId(Long accountId, Pageable pageable);

        Page<TransactionEntity> findByAccountIdAndCreatedAtBetween(
                        Long accountId, LocalDateTime from, LocalDateTime to, Pageable pageable);

        @Query("SELECT t.type AS type, COUNT(t) AS count, COALESCE(SUM(t.amount), 0) AS totalAmount " +
                        "FROM Transaction t WHERE t.accountId = :accountId " +
                        "AND t.createdAt >= :from AND t.createdAt <= :to " +
                        "GROUP BY t.type")
        List<Object[]> getTransactionSummary(
                        @Param("accountId") Long accountId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);
}
