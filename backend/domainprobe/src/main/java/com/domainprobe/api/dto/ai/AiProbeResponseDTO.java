package com.domainprobe.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProbeResponseDTO {

    private String status;

    private String summary;

    private AiSectionDTO domain;

    private AiSectionDTO dns;

    private AiSectionDTO ssl;

    private AiSectionDTO http;

    private AiSectionDTO dnssec;

    private AiSectionDTO dmarc;

    private AiSectionDTO ip;

    private AiSectionDTO technology;

    private String recommendation;
}