package com.domainprobe.api.dto.http;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityHeadersDTO {

    private String contentSecurityPolicy;

    private String strictTransportSecurity;

    private String xContentTypeOptions;

    private String xFrameOptions;

    private String referrerPolicy;

    private String permissionsPolicy;
}