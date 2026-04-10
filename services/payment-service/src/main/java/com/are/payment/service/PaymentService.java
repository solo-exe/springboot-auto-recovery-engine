package com.are.payment.service;

import com.are.payment.config.RabbitConfig;
import com.are.payment.dto.PaymentRequest;
import com.are.payment.dto.PaymentResponse;
import com.are.common.model.PaymentEntity;
import com.are.common.model.PaymentStatus;
import com.are.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final WebClient accountServiceWebClient;
    private final RabbitTemplate rabbitTemplate;

    public PaymentService(PaymentRepository paymentRepository,
            WebClient accountServiceWebClient,
            RabbitTemplate rabbitTemplate) {
        this.paymentRepository = paymentRepository;
        this.accountServiceWebClient = accountServiceWebClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    @CircuitBreaker(name = "accountServiceCB", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment: {} -> {}, amount: {} {}",
                request.fromAccountId(), request.toAccountId(), request.amount(), request.currency());

        // Create payment record
        PaymentEntity payment = new PaymentEntity();
        payment.setFromAccountId(request.fromAccountId());
        payment.setToAccountId(request.toAccountId());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setDescription(request.description());
        payment.setReference("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setCorrelationId(MDC.get("correlationId"));
        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        try {
            // Step 1: Verify source account exists and has sufficient balance
            Map<String, Object> sourceAccount = verifyAccount(request.fromAccountId());
            BigDecimal sourceBalance = new BigDecimal(sourceAccount.get("balance").toString());

            if (sourceBalance.compareTo(request.amount()) < 0) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Insufficient balance");
                paymentRepository.save(payment);
                log.warn("Payment {} failed: insufficient balance", payment.getId());
                return toResponse(payment);
            }

            // Step 2: Verify destination account exists
            verifyAccount(request.toAccountId());

            // Step 3: Debit source account
            updateAccountBalance(request.fromAccountId(), request.amount().negate());

            // Step 4: Credit destination account
            updateAccountBalance(request.toAccountId(), request.amount());

            // Step 5: Mark payment as completed
            payment.setStatus(PaymentStatus.COMPLETED);
            payment = paymentRepository.save(payment);
            log.info("Payment {} completed successfully", payment.getId());

            // Step 6: Send async notification
            sendNotification(payment);

            return toResponse(payment);

        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            log.error("Payment {} failed: {}", payment.getId(), e.getMessage(), e);
            return toResponse(payment);
        }
    }

    public PaymentResponse processPaymentFallback(PaymentRequest request, Throwable t) {
        log.warn("Circuit breaker open — payment processing fallback triggered: {}", t.getMessage());
        PaymentEntity payment = new PaymentEntity();
        payment.setFromAccountId(request.fromAccountId());
        payment.setToAccountId(request.toAccountId());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Service temporarily unavailable (circuit breaker open)");
        payment.setCorrelationId(MDC.get("correlationId"));
        payment = paymentRepository.save(payment);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Can only refund completed payments");
        }

        log.info("Processing refund for payment {}", paymentId);

        // Reverse the balance changes
        updateAccountBalance(payment.getToAccountId(), payment.getAmount().negate());
        updateAccountBalance(payment.getFromAccountId(), payment.getAmount());

        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);
        log.info("Refund for payment {} completed", paymentId);

        sendNotification(payment);
        return toResponse(payment);
    }

    public PaymentResponse getPayment(Long id) {
        PaymentEntity payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + id));
        return toResponse(payment);
    }

    public Page<PaymentResponse> getPayments(Long accountId, PaymentStatus status, Pageable pageable) {
        if (accountId != null) {
            return paymentRepository.findByFromAccountId(accountId, pageable).map(this::toResponse);
        }
        if (status != null) {
            return paymentRepository.findByStatus(status, pageable).map(this::toResponse);
        }
        return paymentRepository.findAll(pageable).map(this::toResponse);
    }

    public Map<String, Object> getPaymentStats() {
        return Map.of(
                "totalPayments", paymentRepository.count(),
                "completedPayments", paymentRepository.countByStatus(PaymentStatus.COMPLETED),
                "failedPayments", paymentRepository.countByStatus(PaymentStatus.FAILED),
                "pendingPayments", paymentRepository.countByStatus(PaymentStatus.PENDING),
                "totalCompletedAmount", paymentRepository.sumCompletedPayments());
    }

    // ----- Private helpers -----

    private Map<String, Object> verifyAccount(Long accountId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> account = accountServiceWebClient.get()
                .uri("/accounts/{id}", accountId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (account == null) {
            throw new RuntimeException("Account not found: " + accountId);
        }
        return account;
    }

    private void updateAccountBalance(Long accountId, BigDecimal amount) {
        accountServiceWebClient.put()
                .uri("/accounts/{id}/balance", accountId)
                .bodyValue(Map.of("amount", amount))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private void sendNotification(PaymentEntity payment) {
        try {
            Map<String, Object> event = Map.of(
                    "paymentId", payment.getId(),
                    "fromAccountId", payment.getFromAccountId(),
                    "toAccountId", payment.getToAccountId(),
                    "amount", payment.getAmount(),
                    "currency", payment.getCurrency(),
                    "status", payment.getStatus().name(),
                    "reference", payment.getReference() != null ? payment.getReference() : "",
                    "correlationId", payment.getCorrelationId() != null ? payment.getCorrelationId() : "");
            rabbitTemplate.convertAndSend(
                    RabbitConfig.NOTIFICATION_EXCHANGE,
                    RabbitConfig.NOTIFICATION_ROUTING_KEY,
                    event);
            log.info("Notification sent for payment {}", payment.getId());
        } catch (Exception e) {
            log.warn("Failed to send notification for payment {}: {}", payment.getId(), e.getMessage());
        }
    }

    private PaymentResponse toResponse(PaymentEntity p) {
        return new PaymentResponse(
                p.getId(),
                p.getFromAccountId(),
                p.getToAccountId(),
                p.getAmount(),
                p.getCurrency(),
                p.getStatus(),
                p.getDescription(),
                p.getReference(),
                p.getCorrelationId(),
                p.getFailureReason(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
