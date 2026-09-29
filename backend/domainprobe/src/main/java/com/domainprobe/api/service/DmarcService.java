package com.domainprobe.api.service;

import com.domainprobe.api.config.DnsConfig;
import com.domainprobe.api.dto.dmarc.DmarcResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DmarcService {

    private final WebClient webClient;
    private final DnsConfig dnsConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ============================================================
       DMARC DATA
       ============================================================ */

    /**
     * Fetches and parses the DMARC record for the supplied domain.
     * DMARC records are stored as TXT records under _dmarc.
     */
    public DmarcResponseDTO fetchDmarcData(final String domainName) {

        final String dmarcDomain = "_dmarc." + domainName;

        return fetchDmarcRecord(dmarcDomain, domainName)
                .map(this::parseDmarcRecord)
                .block();
    }

    /**
     * Fetches the DMARC TXT record.
     */
    private Mono<String> fetchDmarcRecord(
            final String dmarcDomain,
            final String domainName
    ) {

        return webClient
                .get()
                .uri(dnsConfig.getBaseUrl(), uriBuilder -> uriBuilder
                        .queryParam("name", dmarcDomain)
                        .queryParam("type", "TXT")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(rawResponse -> {

                    log.info(
                            "[DMARC][RAW] TXT response | domain={} => {}",
                            domainName,
                            rawResponse
                    );

                    if (!StringUtils.hasText(rawResponse)) {
                        log.warn(
                                "[DMARC] Empty TXT response | domain={}",
                                domainName
                        );

                        return "";
                    }

                    return extractDmarcRecord(
                            rawResponse,
                            domainName
                    );
                })
                .onErrorResume(exception -> {

                    log.warn(
                            "[DMARC] Failed to fetch DMARC record | domain={} | error={}",
                            domainName,
                            exception.getMessage()
                    );

                    return Mono.just("");
                });
    }

    /**
     * Extracts the DMARC TXT record from the DNS response.
     */
    private String extractDmarcRecord(
            final String rawResponse,
            final String domainName
    ) {

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode answers = root.path("Answer");

            if (!answers.isArray()) {
                return "";
            }

            for (JsonNode answer : answers) {

                String data = answer.path("data").asText(null);

                if (!StringUtils.hasText(data)) {
                    continue;
                }

                if (data.trim().toLowerCase().startsWith("v=dmarc1")) {
                    return data.trim();
                }
            }

            return "";

        } catch (Exception exception) {

            log.warn(
                    "[DMARC] Failed to parse TXT response | domain={} | error={}",
                    domainName,
                    exception.getMessage()
            );

            return "";
        }
    }

    /**
     * Parses the DMARC record into a structured response.
     */
    private DmarcResponseDTO parseDmarcRecord(
            final String record
    ) {

        if (!StringUtils.hasText(record)) {
            return DmarcResponseDTO.builder()
                    .present(false)
                    .record(null)
                    .version(null)
                    .policy(null)
                    .subdomainPolicy(null)
                    .percentage(null)
                    .aggregateReports(List.of())
                    .forensicReports(List.of())
                    .dkimAlignment(null)
                    .spfAlignment(null)
                    .failureOptions(null)
                    .build();
        }

        String version = null;
        String policy = null;
        String subdomainPolicy = null;
        Integer percentage = null;

        List<String> aggregateReports = new ArrayList<>();
        List<String> forensicReports = new ArrayList<>();

        String dkimAlignment = null;
        String spfAlignment = null;
        String failureOptions = null;

        String[] tags = record.split(";");

        for (String tag : tags) {

            String trimmedTag = tag.trim();

            if (!StringUtils.hasText(trimmedTag)) {
                continue;
            }

            String[] parts = trimmedTag.split("=", 2);

            if (parts.length != 2) {
                continue;
            }

            String key = parts[0].trim().toLowerCase();
            String value = parts[1].trim();

            switch (key) {

                case "v":
                    version = value;
                    break;

                case "p":
                    policy = value;
                    break;

                case "sp":
                    subdomainPolicy = value;
                    break;

                case "pct":
                    try {
                        percentage = Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        log.warn(
                                "[DMARC] Invalid percentage value | value={}",
                                value
                        );
                    }
                    break;

                case "rua":
                    aggregateReports.addAll(parseReportAddresses(value));
                    break;

                case "ruf":
                    forensicReports.addAll(parseReportAddresses(value));
                    break;

                case "adkim":
                    dkimAlignment = value;
                    break;

                case "aspf":
                    spfAlignment = value;
                    break;

                case "fo":
                    failureOptions = value;
                    break;

                default:
                    break;
            }
        }

        return DmarcResponseDTO.builder()
                .present(true)
                .record(record)
                .version(version)
                .policy(policy)
                .subdomainPolicy(subdomainPolicy)
                .percentage(percentage)
                .aggregateReports(aggregateReports)
                .forensicReports(forensicReports)
                .dkimAlignment(dkimAlignment)
                .spfAlignment(spfAlignment)
                .failureOptions(failureOptions)
                .build();
    }

    /**
     * Parses comma-separated DMARC report addresses.
     */
    private List<String> parseReportAddresses(
            final String value
    ) {

        if (!StringUtils.hasText(value)) {
            return List.of();
        }

        return List.of(value.split(","))
                .stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}