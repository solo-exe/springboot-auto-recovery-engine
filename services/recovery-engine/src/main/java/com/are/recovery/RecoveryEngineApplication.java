package com.are.recovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.are.recovery", "com.are.common"})
public class RecoveryEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecoveryEngineApplication.class, args);
    }
}
