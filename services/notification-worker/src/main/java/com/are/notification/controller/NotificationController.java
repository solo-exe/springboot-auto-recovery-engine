package com.are.notification.controller;

import com.are.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public endpoint to retrieve recent notifications.
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
}
