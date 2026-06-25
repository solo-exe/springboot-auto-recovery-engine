package com.are.recovery.audit;

import com.are.recovery.event.RecoveryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AuditLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String AUDIT_FILE = "logs/recovery-audit.json";

    @EventListener
    public void onRecoveryEvent(RecoveryEvent event) {
        Map<String, Object> auditRecord = new LinkedHashMap<>();
        auditRecord.put("timestamp", Instant.now().toString());
        auditRecord.put("correlationId", event.getCorrelationId());
        auditRecord.put("service", event.getServiceName());
        auditRecord.put("alertName", event.getAlertName());
        auditRecord.put("eventType", "ALERT_RECEIVED");
        
        writeAuditLog(auditRecord);
    }
    
    public void logAction(String correlationId, String service, String action, String outcome) {
        Map<String, Object> auditRecord = new LinkedHashMap<>();
        auditRecord.put("timestamp", Instant.now().toString());
        auditRecord.put("correlationId", correlationId);
        auditRecord.put("service", service);
        auditRecord.put("action", action);
        auditRecord.put("outcome", outcome);
        auditRecord.put("eventType", "ACTION_EXECUTED");
        writeAuditLog(auditRecord);
    }

    private synchronized void writeAuditLog(Map<String, Object> record) {
        try {
            File f = new File(AUDIT_FILE);
            if (!f.exists()) {
                f.getParentFile().mkdirs();
            }
            try (PrintWriter out = new PrintWriter(new FileWriter(f, true))) {
                out.println(mapper.writeValueAsString(record));
            }
        } catch (IOException e) {
            log.error("Failed to write to audit log", e);
        }
    }
}
