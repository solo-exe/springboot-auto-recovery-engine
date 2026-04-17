package com.are.payment.controller;

import com.are.payment.dto.InitiatePaymentRequest;
import com.are.payment.dto.PaymentResponse;
import com.are.payment.service.PaymentService;
import com.are.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Payment endpoints — all require JWT authentication.
 * User identity comes from X-User-Id header forwarded by the gateway.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        log.info("Initiating payment for user ID: {} with request: {}", userId, request);
        PaymentResponse response = paymentService.initiatePayment(userId, request);
        log.info("Successfully initiated payment with ID: {}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long paymentId) {
        log.debug("Fetching payment ID: {} for user ID: {}", paymentId, userId);
        PaymentResponse response = paymentService.getPayment(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getPaymentHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.debug("Fetching payment history for user ID: {}, page: {}, size: {}", userId, page, size);
        Page<PaymentResponse> history = paymentService.getPaymentHistory(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}
