package com.domainprobe.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "application", "DomainProbe",
                "status", "UP",
                "message", "Domain intelligence API is running"
        );
    }
}