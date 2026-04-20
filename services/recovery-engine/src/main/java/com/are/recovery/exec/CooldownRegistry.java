package com.are.recovery.exec;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CooldownRegistry {
    
    private final Map<String, Instant> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_SECONDS = 120;

    public boolean canExecute(String serviceName) {
        Instant lastExecuted = cooldowns.get(serviceName);
        if (lastExecuted == null) {
            return true;
        }
        return Instant.now().isAfter(lastExecuted.plusSeconds(COOLDOWN_SECONDS));
    }

    public void recordExecution(String serviceName) {
        cooldowns.put(serviceName, Instant.now());
    }
}
