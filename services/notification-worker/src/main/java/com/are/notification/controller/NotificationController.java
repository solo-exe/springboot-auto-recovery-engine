package com.are.notification.controller;

import com.are.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public endpoint to retrieve recent notifications and test configuration.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentNotifications() {
        List<Map<String, Object>> recent = notificationService.getRecentNotifications();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", recent,
                "count", recent.size()));
    }

    @PostMapping("/test-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestParam String to) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("email", to);
        notification.put("reference", "TEST-REF-12345");
        notification.put("amount", new BigDecimal("1000.00"));
        notification.put("destination", "0011223344");

        notificationService.processNotification(notification);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test email triggered for " + to + ". Check service logs for results."));
    }

    @PostMapping("/otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@RequestBody Map<String, Object> event) {
        notificationService.processOtpNotification(event);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "OTP notification processed."));
    }
}
