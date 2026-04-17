package com.are.account.controller;

import com.are.common.dto.ApiResponse;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fault injection endpoints for testing resilience.
 * These endpoints do NOT require authentication.
 */
@RestController
@RequestMapping("/fault")
public class FaultSimulationController {

    private static final Logger log = LoggerFactory.getLogger(FaultSimulationController.class);

    private final AtomicBoolean simulateUnresponsive = new AtomicBoolean(false);
    private final AtomicInteger errorRatePercent = new AtomicInteger(0);
    private final AtomicBoolean simulateMemoryLeak = new AtomicBoolean(false);
    private final AtomicBoolean simulateCpuSpike = new AtomicBoolean(false);
    private final List<byte[]> leakedMemory = new ArrayList<>();

    public FaultSimulationController(MeterRegistry meterRegistry) {
        // Register are.fault.active gauge
        Gauge.builder("are.fault.active", this,
                ctrl -> (ctrl.simulateUnresponsive.get() || ctrl.errorRatePercent.get() > 0 ||
                        ctrl.simulateMemoryLeak.get() || ctrl.simulateCpuSpike.get()) ? 1.0 : 0.0)
                .description("Whether any fault simulation is currently active")
                .register(meterRegistry);
    }

    @PostMapping("/unresponsive")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleUnresponsive(@RequestBody Map<String, Boolean> body) {
        boolean enable = body.getOrDefault("enable", false);
        simulateUnresponsive.set(enable);
        log.warn("FAULT_SIMULATION: Unresponsive state set to {}", enable);
        return ResponseEntity.ok(ApiResponse.success(Map.of("fault", "unresponsive", "active", enable)));
    }

    @PostMapping("/error-rate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setErrorRate(@RequestBody Map<String, Integer> body) {
        int rate = body.getOrDefault("rate", 0);
        errorRatePercent.set(rate);
        log.warn("FAULT_SIMULATION: Error rate set to {}%", rate);
        return ResponseEntity.ok(ApiResponse.success(Map.of("fault", "error-rate", "rate", rate)));
    }

    @PostMapping("/memory-leak")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleMemoryLeak(@RequestBody Map<String, Boolean> body) {
        boolean enable = body.getOrDefault("enable", false);
        simulateMemoryLeak.set(enable);
        log.warn("FAULT_SIMULATION: Memory leak simulation set to {}", enable);
        if (enable) {
            // Background thread allocating 1MB every 500ms
            Thread.ofVirtual().name("memory-leak-sim").start(() -> {
                while (simulateMemoryLeak.get()) {
                    leakedMemory.add(new byte[1024 * 1024]); // 1MB
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        } else {
            leakedMemory.clear();
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("fault", "memory-leak", "active", enable)));
    }

    @PostMapping("/cpu-spike")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleCpuSpike(@RequestBody Map<String, Boolean> body) {
        boolean enable = body.getOrDefault("enable", false);
        simulateCpuSpike.set(enable);
        log.warn("FAULT_SIMULATION: CPU spike simulation set to {}", enable);
        if (enable) {
            int cores = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < cores; i++) {
                Thread.ofVirtual().name("cpu-spike-sim-" + i).start(() -> {
                    while (simulateCpuSpike.get()) {
                        Math.random();
                    }
                });
            }
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("fault", "cpu-spike", "active", enable)));
    }

    // --- Accessors for FaultInterceptor ---

    public boolean isUnresponsive() {
        return simulateUnresponsive.get();
    }

    public int getErrorRate() {
        return errorRatePercent.get();
    }

    public boolean isAnyFaultActive() {
        return simulateUnresponsive.get() || errorRatePercent.get() > 0 ||
                simulateMemoryLeak.get() || simulateCpuSpike.get();
    }
}
