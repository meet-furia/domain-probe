package com.domainprobe.api.dto.ssl;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SslResponseDTO {

    private SslCertificateDTO certificate;

    private HttpsResponseDTO https;
}