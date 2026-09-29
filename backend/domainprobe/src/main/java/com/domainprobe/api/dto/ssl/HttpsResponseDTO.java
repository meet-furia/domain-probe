package com.domainprobe.api.dto.ssl;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpsResponseDTO {

    private Integer statusCode;

    private boolean hstsEnabled;

    private boolean httpRedirectsToHttps;
}