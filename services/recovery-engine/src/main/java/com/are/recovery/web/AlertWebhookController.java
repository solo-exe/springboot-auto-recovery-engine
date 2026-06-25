package com.are.recovery.web;

import com.are.recovery.event.EventBus;
import com.are.recovery.event.RecoveryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
public class AlertWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhookController.class);
    private final EventBus eventBus;

    public AlertWebhookController(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostMapping
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> receiveAlert(@RequestBody Map<String, Object> payload) {
        log.info("Received alert payload: {}", payload);

        if (payload.containsKey("alerts")) {
            List<Map<String, Object>> alerts = (List<Map<String, Object>>) payload.get("alerts");
            for (Map<String, Object> alert : alerts) {
                String status = (String) alert.get("status");
                
                Map<String, String> labels = (Map<String, String>) alert.get("labels");
                if (labels == null) continue;
                
                String alertName = labels.get("alertname");
                String serviceName = labels.get("job");
                if (serviceName == null) {
                    serviceName = labels.get("container_name"); 
                }
                if (serviceName == null) {
                   serviceName = labels.get("service");
                }
                if (serviceName != null && serviceName.startsWith("are-")) {
                     serviceName = serviceName.substring(4);
                }

                if ("firing".equals(status)) {
                    String correlationId = UUID.randomUUID().toString();
                    log.info("Firing alert detected: {} for service {}", alertName, serviceName);
                    
                    RecoveryEvent event = new RecoveryEvent(this, serviceName, alertName, correlationId, alert);
                    eventBus.publish(event);
                } else if ("resolved".equals(status)) {
                    log.info("Resolved alert detected: {} for service {}", alertName, serviceName);
                }
            }
        }
        return ResponseEntity.ok().build();
    }
}
