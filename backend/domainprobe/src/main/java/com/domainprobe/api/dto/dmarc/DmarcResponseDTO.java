package com.domainprobe.api.dto.dmarc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmarcResponseDTO {

    private boolean present;

    private String record;

    private String version;

    private String policy;

    private String subdomainPolicy;

    private Integer percentage;

    private List<String> aggregateReports;

    private List<String> forensicReports;

    private String dkimAlignment;

    private String spfAlignment;

    private String failureOptions;
}