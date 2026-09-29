package com.domainprobe.api.config;

import com.google.genai.Client;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Bean
    public Client geminiClient() {

        return Client.builder()
                .apiKey(apiKey)
                .build();
    }
}