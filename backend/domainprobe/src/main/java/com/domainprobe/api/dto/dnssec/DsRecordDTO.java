package com.domainprobe.api.dto.dnssec;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DsRecordDTO {

    private String keyTag;

    private String algorithm;

    private String digestType;

    private String digest;
}