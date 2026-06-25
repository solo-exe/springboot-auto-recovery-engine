package com.are.recovery.exec;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CooldownRegistry {
    
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    
    @org.springframework.beans.factory.annotation.Value("${recovery.cooldown.seconds:120}")
    private long cooldownSeconds;

    public boolean canExecute(String serviceName) {
        Instant lastExecuted = cooldowns.get(serviceName);
        if (lastExecuted == null) {
            return true;
        }
        return Instant.now().isAfter(lastExecuted.plusSeconds(cooldownSeconds));
    }

    public void recordExecution(String serviceName) {
        cooldowns.put(serviceName, Instant.now());
    }
}
