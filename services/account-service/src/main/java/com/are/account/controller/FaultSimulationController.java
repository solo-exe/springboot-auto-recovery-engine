package com.are.account.controller;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/fault")
public class FaultSimulationController {

    private final AtomicBoolean simulateUnresponsive = new AtomicBoolean(false);
    private final AtomicInteger errorRatePercent = new AtomicInteger(0);
    private final AtomicBoolean simulateMemoryLeak = new AtomicBoolean(false);
    private final AtomicBoolean simulateCpuSpike = new AtomicBoolean(false);
    private final AtomicBoolean simulateDbPoolExhaust = new AtomicBoolean(false);
    private final List<byte[]> leakedMemory = new ArrayList<>();

    @PostMapping("/unresponsive")
    public String toggleUnresponsive(@RequestParam boolean enable) {
        simulateUnresponsive.set(enable);
        return "Unresponsive simulation: " + enable;
    }

    @PostMapping("/error-rate")
    public String setErrorRate(@RequestParam int percent) {
        errorRatePercent.set(percent);
        return "Error rate set to: " + percent + "%";
    }

    @PostMapping("/memory-leak")
    public String toggleMemoryLeak(@RequestParam boolean enable) {
        simulateMemoryLeak.set(enable);
        if (!enable)
            leakedMemory.clear();
        return "Memory leak simulation: " + enable;
    }

    @PostMapping("/cpu-spike")
    public String toggleCpuSpike(@RequestParam boolean enable) {
        simulateCpuSpike.set(enable);
        if (enable) {
            Thread.ofVirtual().start(() -> {
                while (simulateCpuSpike.get()) {
                    Math.random();
                }
            });
        }
        return "CPU spike simulation: " + enable;
    }

    @PostMapping("/db-pool-exhaust")
    public String toggleDbPoolExhaust(@RequestParam boolean enable) {
        simulateDbPoolExhaust.set(enable);
        return "DB pool exhaustion simulation: " + enable;
    }

    public boolean isUnresponsive() {
        return simulateUnresponsive.get();
    }

    public int getErrorRate() {
        return errorRatePercent.get();
    }

    public boolean isDbPoolExhaust() {
        return simulateDbPoolExhaust.get();
    }

    public void leakMemory() {
        if (simulateMemoryLeak.get()) {
            leakedMemory.add(new byte[1024 * 1024]);
        }
    }
}
