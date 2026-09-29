package com.domainprobe.api.dto.ip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpRecordDTO {

    private String ip;
    private String asn;
    private String asName;
    private String asDomain;
    private String countryCode;
    private String country;
    private String continentCode;
    private String continent;
}