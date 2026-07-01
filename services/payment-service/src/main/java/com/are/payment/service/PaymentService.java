package com.are.payment.service;

import java.time.LocalDateTime;
import java.util.*;

import com.are.payment.config.RabbitConfig;
import com.are.payment.dto.InitiatePaymentRequest;
import com.are.payment.dto.PaymentResponse;
import com.are.common.exception.InsufficientFundsException;
import com.are.common.exception.ResourceNotFoundException;
import com.are.common.exception.ForbiddenException;
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
import com.are.common.security.UserContext;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Payment processing service.
 * Uses internal account-service endpoints for balance verification and debit.
 */
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
    @CircuitBreaker(name = "accountServiceCB", fallbackMethod = "initiatePaymentFallback")
    public PaymentResponse initiatePayment(InitiatePaymentRequest request) {
        Long userId = UserContext.getUserId();
        log.info("Initiating payment for user {}: {} to {}",
                userId, request.amount(), request.destinationAccountNumber());

        // Step 1: Get source account details from account-service
        Map<String, Object> accountData = getInternalAccount(userId);
        Long sourceAccountId = ((Number) accountData.get("accountId")).longValue();
        String sourceAccountNumber = (String) accountData.get("accountNumber");
        String customerEmail = (String) accountData.get("email");
        BigDecimal balance = new BigDecimal(accountData.get("balance").toString());
        String status = accountData.get("status").toString();

        // Step 2: Validate account is ACTIVE and has sufficient balance
        if (!"ACTIVE".equals(status)) {
            throw new ForbiddenException("Source account is not active");
        }
        if (balance.compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance. Available: " + balance + ", requested: " + request.amount());
        }

        // Step 3: Create payment record with PROCESSING status
        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PaymentEntity payment = new PaymentEntity();
        payment.setFromAccountId(sourceAccountId);
        payment.setToAccountId(0L); // We store destination by account number
        payment.setAmount(request.amount());
        payment.setCurrency("NGN");
        payment.setDescription(request.description());
        payment.setReference(reference);
        payment.setCorrelationId(MDC.get("correlationId"));
        payment.setStatus(PaymentStatus.PROCESSING);
        payment = paymentRepository.save(payment);

        try {
            // Step 4: Debit source account via internal endpoint
            debitAccount(sourceAccountId, request.amount(), reference);

            // Step 5: Mark payment as COMPLETED
            payment.setStatus(PaymentStatus.COMPLETED);
            payment = paymentRepository.save(payment);
            log.info("Payment {} completed successfully", payment.getId());

            // Step 6: Publish event to RabbitMQ
            publishPaymentEvent(payment, sourceAccountNumber, request.destinationAccountNumber(), customerEmail);

            return toResponse(payment);

        } catch (InsufficientFundsException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            throw e;
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            paymentRepository.save(payment);
            log.error("Payment {} failed: {}", payment.getId(), e.getMessage(), e);
            throw new RuntimeException("Payment processing failed: " + e.getMessage(), e);
        }
    }

    public PaymentResponse initiatePaymentFallback(InitiatePaymentRequest request, Throwable t) {
        Long userId = UserContext.getUserId();
        log.warn("Circuit breaker open — payment fallback for user {}: {}", userId, t.getMessage());
        PaymentEntity payment = new PaymentEntity();
        payment.setFromAccountId(0L);
        payment.setToAccountId(0L);
        payment.setAmount(request.amount());
        payment.setCurrency("NGN");
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Service temporarily unavailable (circuit breaker open)");
        payment.setCorrelationId(MDC.get("correlationId"));
        payment.setReference("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment = paymentRepository.save(payment);
        return toResponse(payment);
    }

    public PaymentResponse getPayment(Long paymentId) {
        Long userId = UserContext.getUserId();
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        // Verify payment belongs to requesting user by checking the source account
        Map<String, Object> accountData = getInternalAccount(userId);
        Long userAccountId = ((Number) accountData.get("accountId")).longValue();
        if (!payment.getFromAccountId().equals(userAccountId)) {
            throw new ForbiddenException("Payment does not belong to this user");
        }

        return toResponse(payment);
    }

    public Page<PaymentResponse> getPaymentHistory(Pageable pageable) {
        Long userId = UserContext.getUserId();
        Map<String, Object> accountData = getInternalAccount(userId);
        Long accountId = ((Number) accountData.get("accountId")).longValue();
        return paymentRepository.findByFromAccountId(accountId, pageable).map(this::toResponse);
    }

    // ----- Private helpers -----

    private Map<String, Object> getInternalAccount(Long userId) {
        if (userId == null) {
            throw new ForbiddenException("Missing User ID");
        }
        Map<String, Object> response = accountServiceWebClient.get()
                .uri("/internal/account/" + userId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("data") == null) {
            throw new ResourceNotFoundException("Account not found for user: " + userId);
        }
        return (Map<String, Object>) response.get("data");
    }

    private void debitAccount(Long accountId, BigDecimal amount, String paymentReference) {
        accountServiceWebClient.post()
                .uri("/internal/debit")
                .bodyValue(Map.of(
                        "accountId", accountId,
                        "amount", amount,
                        "paymentReference", paymentReference))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private void publishPaymentEvent(PaymentEntity payment, String sourceAccountNumber,
            String destinationAccountNumber, String customerEmail) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("sourceAccountId", payment.getFromAccountId());
            event.put("destinationAccountNumber", destinationAccountNumber);
            event.put("amount", payment.getAmount());
            event.put("reference", payment.getReference());
            event.put("customerEmail", customerEmail);
            event.put("timestamp", LocalDateTime.now().toString());
            event.put("correlationId", payment.getCorrelationId() != null ? payment.getCorrelationId() : "");

            rabbitTemplate.convertAndSend(
                    RabbitConfig.PAYMENT_EXCHANGE,
                    RabbitConfig.PAYMENT_COMPLETED_ROUTING_KEY,
                    event);
            log.info("Payment event published for payment {} (email: {})", payment.getId(), customerEmail);
        } catch (Exception e) {
            log.warn("Failed to publish payment event for {}: {}", payment.getId(), e.getMessage());
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
