package com.are.recovery.event;

import org.springframework.context.ApplicationEvent;
import java.util.Map;

public class RecoveryEvent extends ApplicationEvent {
    private final String serviceName;
    private final String alertName;
    private final String correlationId;
    private final Map<String, Object> metadata;

    public RecoveryEvent(Object source, String serviceName, String alertName, String correlationId, Map<String, Object> metadata) {
        super(source);
        this.serviceName = serviceName;
        this.alertName = alertName;
        this.correlationId = correlationId;
        this.metadata = metadata;
    }

    public String getServiceName() { return serviceName; }
    public String getAlertName() { return alertName; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getMetadata() { return metadata; }
}
