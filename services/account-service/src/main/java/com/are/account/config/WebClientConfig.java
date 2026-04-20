package com.are.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${notification-worker.url:http://localhost:8085}")
    private String notificationWorkerUrl;

    @Bean
    public WebClient notificationWorkerWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .baseUrl(notificationWorkerUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
