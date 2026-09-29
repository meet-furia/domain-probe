package com.domainprobe.api.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class GroqConfig {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.base-url}")
    private String baseUrl;
}