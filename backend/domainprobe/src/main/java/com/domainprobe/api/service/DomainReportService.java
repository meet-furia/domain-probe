package com.domainprobe.api.service;

import com.domainprobe.api.dto.dmarc.DmarcResponseDTO;
import com.domainprobe.api.dto.dns.DnsResponseDTO;
import com.domainprobe.api.dto.dnssec.DnssecResponseDTO;
import com.domainprobe.api.dto.domain.DomainReportDTO;
import com.domainprobe.api.dto.http.HttpResponseDTO;
import com.domainprobe.api.dto.ip.IpResponseDTO;
import com.domainprobe.api.dto.rdap.RdapDomainDTO;
import com.domainprobe.api.dto.ssl.SslResponseDTO;
import com.domainprobe.api.dto.technology.TechnologyResponseDTO;
import com.domainprobe.api.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class DomainReportService {

    private final RdapService rdapService;
    private final DnsService dnsService;
    private final SslService sslService;
    private final HttpService httpService;
    private final DnssecService dnssecService;
    private final DmarcService dmarcService;
    private final IpService ipService;
    private final TechnologyDetectionService technologyDetectionService;

    /**
     * Creates a domain report using RDAP registration data.
     */
    public DomainReportDTO generateDomainReport(final String input) {
        String domainName = normalizeDomainName(input);
        log.info("Generating domain report for {}", domainName);

        RdapDomainDTO rdapData = rdapService.fetchDomainData(domainName);

        DnsResponseDTO dnsData = dnsService.fetchDnsData(domainName);

        IpResponseDTO ipData = ipService.fetchIpData(dnsData);

        SslResponseDTO sslData = sslService.fetchSslData(domainName);

        HttpResponseDTO httpData = httpService.fetchHttpData(domainName);

        TechnologyResponseDTO technologyData = technologyDetectionService.detectTechnologies(httpData);

        DnssecResponseDTO dnssecData = dnssecService.fetchDnssecData(domainName);

        DmarcResponseDTO dmarcData = dmarcService.fetchDmarcData(domainName);

        return DomainReportDTO.builder()
                .input(input)
                .domainName(domainName)
                .analyzedAt(Instant.now())
                .rdap(rdapData)
                .dns(dnsData)
                .ssl(sslData)
                .http(httpData)
                .dnssec(dnssecData)
                .dmarc(dmarcData)
                .ip(ipData)
                .technology(technologyData)
                .build();
    }

    private String normalizeDomainName(String input) {
        if (!StringUtils.hasText(input)) {
            throw new BadRequestException("Domain name cannot be empty");
        }

        String domainName = input.trim();
        boolean hasScheme = domainName.matches("(?i)^[a-z][a-z0-9+.-]*://.*");
        boolean urlInput = hasScheme
                || domainName.contains("/")
                || domainName.contains("?")
                || domainName.contains("#");
        String candidate = hasScheme ? domainName : urlInput ? "https://" + domainName : "//" + domainName;

        if (urlInput) {
            URI uri;
            try {
                uri = URI.create(candidate);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Domain name or URL is invalid");
            }

            if (uri.getRawUserInfo() != null) {
                throw new BadRequestException("Domain URLs must not contain user information");
            }

            domainName = uri.getHost();
            if (!StringUtils.hasText(domainName) && uri.getRawAuthority() != null) {
                String authority = uri.getRawAuthority();
                int portSeparator = authority.lastIndexOf(':');
                boolean hasPort = portSeparator > 0
                        && authority.substring(portSeparator + 1).matches("\\d+");
                domainName = hasPort ? authority.substring(0, portSeparator) : authority;
            }
        }

        if (!StringUtils.hasText(domainName)) {
            throw new BadRequestException("Domain name or URL must include a valid hostname");
        }

        try {
            domainName = IDN.toASCII(domainName.trim(), IDN.USE_STD3_ASCII_RULES)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Domain name is invalid");
        }

        if (domainName.endsWith(".")) {
            domainName = domainName.substring(0, domainName.length() - 1);
        }

        if (domainName.startsWith("www.")) {
            domainName = domainName.substring(4);
        }

        validateDomainName(domainName);
        return domainName;
    }

    private void validateDomainName(String domainName) {
        if (domainName.length() > 253) {
            throw new BadRequestException("Domain name is too long");
        }

        String[] labels = domainName.split("\\.", -1);
        if (labels.length < 2) {
            throw new BadRequestException("A fully qualified domain name is required");
        }

        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) {
                throw new BadRequestException("Domain name is invalid");
            }
        }
    }
}
