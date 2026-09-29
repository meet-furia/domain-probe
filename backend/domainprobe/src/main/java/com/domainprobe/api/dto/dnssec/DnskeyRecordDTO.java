package com.domainprobe.api.dto.dnssec;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnskeyRecordDTO {

    private String flags;

    private String protocol;

    private String algorithm;

    private String publicKey;
}