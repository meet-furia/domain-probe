package com.domainprobe.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ipinfo")
public class IpInfoConfig {

    private String baseUrl;

    private String token;
}