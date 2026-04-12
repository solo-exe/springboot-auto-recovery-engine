package com.are.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Processes payment notifications and stores them in-memory (max 50 entries).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ENTRIES = 50;

    private final ConcurrentLinkedDeque<Map<String, Object>> recentNotifications = new ConcurrentLinkedDeque<>();
    private final AtomicInteger count = new AtomicInteger(0);

    public void processNotification(Map<String, Object> event) {
        String reference = String.valueOf(event.getOrDefault("reference", "unknown"));
        Object amount = event.getOrDefault("amount", "0");
        String destination = String.valueOf(event.getOrDefault("destinationAccountNumber", "unknown"));
        String correlationId = String.valueOf(event.getOrDefault("correlationId", ""));

        log.info("Payment notification processed: reference={}, amount={}, destination={}",
                reference, amount, destination);

        // Build notification entry
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", count.incrementAndGet());
        entry.put("reference", reference);
        entry.put("amount", amount);
        entry.put("destinationAccountNumber", destination);
        entry.put("correlationId", correlationId);
        entry.put("processedAt", LocalDateTime.now().toString());
        entry.putAll(event);

        // Add to deque, evict oldest if over limit
        recentNotifications.addFirst(entry);
        while (recentNotifications.size() > MAX_ENTRIES) {
            recentNotifications.removeLast();
        }
    }

    public List<Map<String, Object>> getRecentNotifications() {
        return new ArrayList<>(recentNotifications);
    }
}
