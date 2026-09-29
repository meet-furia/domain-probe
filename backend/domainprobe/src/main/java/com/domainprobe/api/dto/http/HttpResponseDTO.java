package com.domainprobe.api.dto.http;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpResponseDTO {

    private Integer statusCode;

    private String finalUrl;

    private String redirectLocation;

    private List<String> redirectChain;

    private long responseTimeMs;

    private String server;

    private String contentType;

    private Long contentLength;

    private String contentEncoding;

    private SecurityHeadersDTO securityHeaders;

    private List<CookieDTO> cookies;

    @JsonIgnore
    private String html;
}