package com.domainprobe.api.dto.ssl;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SslCertificateDTO {

    private boolean enabled;

    private boolean valid;

    private String issuer;

    private String subject;

    private OffsetDateTime validFrom;

    private OffsetDateTime expiresAt;

    private long daysRemaining;

    private String tlsVersion;

    private List<String> domains;
}