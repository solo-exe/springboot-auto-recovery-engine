package com.are.recovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "recovery")
public class RecoveryMatrixProperties {
    
    private List<RecoveryRule> matrix;

    public List<RecoveryRule> getMatrix() {
        return matrix;
    }

    public void setMatrix(List<RecoveryRule> matrix) {
        this.matrix = matrix;
    }

    public static class RecoveryRule {
        private String fault;
        private String signal;
        private String primaryAction;
        private String secondaryAction;
        private double threshold;
        private String window;

        public String getFault() { return fault; }
        public void setFault(String fault) { this.fault = fault; }

        public String getSignal() { return signal; }
        public void setSignal(String signal) { this.signal = signal; }

        public String getPrimaryAction() { return primaryAction; }
        public void setPrimaryAction(String primaryAction) { this.primaryAction = primaryAction; }

        public String getSecondaryAction() { return secondaryAction; }
        public void setSecondaryAction(String secondaryAction) { this.secondaryAction = secondaryAction; }

        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }

        public String getWindow() { return window; }
        public void setWindow(String window) { this.window = window; }
    }
}
