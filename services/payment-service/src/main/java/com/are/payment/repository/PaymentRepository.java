package com.are.payment.repository;

import com.are.common.model.PaymentEntity;
import com.are.common.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Page<PaymentEntity> findByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId, Pageable pageable);

    Page<PaymentEntity> findByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentEntity> findByFromAccountId(Long fromAccountId, Pageable pageable);

    List<PaymentEntity> findByCorrelationId(String correlationId);

    @Query("SELECT COUNT(p) FROM PaymentEntity p WHERE p.status = :status")
    long countByStatus(@Param("status") PaymentStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentEntity p WHERE p.status = 'COMPLETED'")
    BigDecimal sumCompletedPayments();
}
