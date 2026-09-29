package com.domainprobe.api.dto.domain;

import com.domainprobe.api.dto.dmarc.DmarcResponseDTO;
import com.domainprobe.api.dto.dns.DnsResponseDTO;
import com.domainprobe.api.dto.dnssec.DnssecResponseDTO;
import com.domainprobe.api.dto.http.HttpResponseDTO;
import com.domainprobe.api.dto.ip.IpResponseDTO;
import com.domainprobe.api.dto.rdap.RdapDomainDTO;
import com.domainprobe.api.dto.ssl.SslResponseDTO;
import com.domainprobe.api.dto.technology.TechnologyResponseDTO;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainReportDTO {

    private String input;

    private String domainName;

    private Instant analyzedAt;

    private RdapDomainDTO rdap;

    private DnsResponseDTO dns;

    private SslResponseDTO ssl;

    private HttpResponseDTO http;

    private DnssecResponseDTO dnssec;

    private DmarcResponseDTO dmarc;

    private IpResponseDTO ip;

    private TechnologyResponseDTO technology;
}