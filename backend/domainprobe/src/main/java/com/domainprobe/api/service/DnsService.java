package com.domainprobe.api.service;

import com.domainprobe.api.config.DnsConfig;
import com.domainprobe.api.dto.dns.DnsResponseDTO;
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
public class DnsService {

    private final WebClient webClient;
    private final DnsConfig dnsConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ============================================================
       DNS DATA
       ============================================================ */

    /**
     * Fetches DNS records for the supplied domain.
     * DNS record types are fetched in parallel.
     * <p>
     * If an individual DNS record lookup fails, the remaining
     * DNS data is still returned.
     */
    public DnsResponseDTO fetchDnsData(final String domainName) {

        return Mono.zip(
                        fetchDnsRecord(domainName, "A"),
                        fetchDnsRecord(domainName, "AAAA"),
                        fetchDnsRecord(domainName, "CNAME"),
                        fetchDnsRecord(domainName, "MX"),
                        fetchDnsRecord(domainName, "NS"),
                        fetchDnsRecord(domainName, "TXT"),
                        fetchDnsRecord(domainName, "CAA"),
                        fetchDnsRecord(domainName, "SOA")
                )
                .map(result -> DnsResponseDTO.builder()
                        .a(result.getT1())
                        .aaaa(result.getT2())
                        .cname(result.getT3())
                        .mx(result.getT4())
                        .ns(result.getT5())
                        .txt(result.getT6())
                        .caa(result.getT7())
                        .soa(result.getT8())
                        .build())
                .block();
    }

    /**
     * Fetches a specific DNS record type.
     * <p>
     * Returns an empty list when the individual DNS lookup fails
     * so that other DNS records can still be returned.
     */
    private Mono<List<String>> fetchDnsRecord(
            final String domainName,
            final String recordType
    ) {

        return webClient
                .get()
                .uri(dnsConfig.getBaseUrl(), uriBuilder -> uriBuilder
                        .queryParam("name", domainName)
                        .queryParam("type", recordType)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(rawResponse -> {

                    log.info(
                            "[DNS][RAW] {} response | domain={} => {}",
                            recordType,
                            domainName,
                            rawResponse
                    );

                    if (!StringUtils.hasText(rawResponse)) {
                        log.warn(
                                "[DNS] Empty {} response | domain={}",
                                recordType,
                                domainName
                        );

                        return List.<String>of();
                    }

                    return parseAnswers(rawResponse, recordType, domainName);
                })
                .onErrorResume(exception -> {

                    log.warn(
                            "[DNS] Failed to fetch {} record | domain={} | error={}",
                            recordType,
                            domainName,
                            exception.getMessage()
                    );

                    return Mono.just(List.<String>of());
                });
    }
    
    /**
     * Parses DNS answer records from the DNS response.
     */
    private List<String> parseAnswers(
            final String rawResponse,
            final String recordType,
            final String domainName
    ) {

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode answers = root.path("Answer");

            if (!answers.isArray()) {
                return List.of();
            }

            List<String> records = new ArrayList<>();

            for (JsonNode answer : answers) {
                String data = answer.path("data").asText(null);

                if (StringUtils.hasText(data)) {
                    records.add(data);
                }
            }

            return records;

        } catch (Exception exception) {

            log.warn(
                    "[DNS] Failed to parse {} response | domain={} | error={}",
                    recordType,
                    domainName,
                    exception.getMessage()
            );

            return List.of();
        }
    }
}