package com.are.notification.controller;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
        Gauge.builder("are.fault.active", this, ctrl ->
                        (ctrl.simulateUnresponsive.get() || ctrl.errorRatePercent.get() > 0 ||
                                ctrl.simulateMemoryLeak.get() || ctrl.simulateCpuSpike.get()) ? 1.0 : 0.0)
                .description("Whether any fault simulation is currently active")
                .register(meterRegistry);
    }

    @PostMapping("/unresponsive")
    public Map<String, Object> toggleUnresponsive(@RequestBody Map<String, Boolean> body) {
        boolean enable = body.getOrDefault("enable", false);
        simulateUnresponsive.set(enable);
        log.warn("FAULT_SIMULATION: Unresponsive state set to {}", enable);
        return Map.of("fault", "unresponsive", "active", enable);
    }

    @PostMapping("/error-rate")
    public Map<String, Object> setErrorRate(@RequestBody Map<String, Integer> body) {
        int rate = body.getOrDefault("rate", 0);
        errorRatePercent.set(rate);
        log.warn("FAULT_SIMULATION: Error rate set to {}%", rate);
        return Map.of("fault", "error-rate", "rate", rate);
    }

    @PostMapping("/memory-leak")
    public Map<String, Object> toggleMemoryLeak(@RequestBody Map<String, Boolean> body) {
        boolean enable = body.getOrDefault("enable", false);
        simulateMemoryLeak.set(enable);
        log.warn("FAULT_SIMULATION: Memory leak simulation set to {}", enable);
        if (enable) {
            Thread.ofVirtual().name("memory-leak-sim").start(() -> {
                while (simulateMemoryLeak.get()) {
                    leakedMemory.add(new byte[1024 * 1024]);
                    try { Thread.sleep(500); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); break;
                    }
                }
            });
        } else {
            leakedMemory.clear();
        }
        return Map.of("fault", "memory-leak", "active", enable);
    }

    @PostMapping("/cpu-spike")
    public Map<String, Object> toggleCpuSpike(@RequestBody Map<String, Boolean> body) {
        boolean enable = body.getOrDefault("enable", false);
        simulateCpuSpike.set(enable);
        log.warn("FAULT_SIMULATION: CPU spike simulation set to {}", enable);
        if (enable) {
            int cores = Runtime.getRuntime().availableProcessors();
            for (int i = 0; i < cores; i++) {
                Thread.ofVirtual().name("cpu-spike-sim-" + i).start(() -> {
                    while (simulateCpuSpike.get()) { Math.random(); }
                });
            }
        }
        return Map.of("fault", "cpu-spike", "active", enable);
    }

    public boolean isUnresponsive() { return simulateUnresponsive.get(); }
    public int getErrorRate() { return errorRatePercent.get(); }
}
