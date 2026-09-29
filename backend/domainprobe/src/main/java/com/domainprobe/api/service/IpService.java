package com.domainprobe.api.service;

import com.domainprobe.api.config.IpInfoConfig;
import com.domainprobe.api.dto.dns.DnsResponseDTO;
import com.domainprobe.api.dto.ip.IpRecordDTO;
import com.domainprobe.api.dto.ip.IpResponseDTO;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class IpService {

    private final WebClient webClient;
    private final IpInfoConfig ipInfoConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ============================================================
       IP / ASN DATA
       ============================================================ */

    /**
     * Fetches IP and ASN information for the IP addresses
     * returned by the DNS service.
     *
     * <p>
     * IPv4 and IPv6 addresses are combined and deduplicated
     * before querying IPinfo.
     */
    public IpResponseDTO fetchIpData(final DnsResponseDTO dnsData) {

        if (dnsData == null) {
            return IpResponseDTO.builder()
                    .records(List.of())
                    .build();
        }

        Set<String> uniqueIps = new LinkedHashSet<>();

        if (dnsData.getA() != null) {
            uniqueIps.addAll(dnsData.getA());
        }

        if (dnsData.getAaaa() != null) {
            uniqueIps.addAll(dnsData.getAaaa());
        }

        if (uniqueIps.isEmpty()) {
            return IpResponseDTO.builder()
                    .records(List.of())
                    .build();
        }

        List<IpRecordDTO> ipRecords = new ArrayList<>();

        for (String ip : uniqueIps) {

            if (!StringUtils.hasText(ip)) {
                continue;
            }

            IpRecordDTO ipData = fetchIpInfo(ip.trim());

            if (ipData != null) {
                ipRecords.add(ipData);
            }
        }

        return IpResponseDTO.builder()
                .records(ipRecords)
                .build();
    }

    /**
     * Fetches IP information from IPinfo Lite.
     */
    private IpRecordDTO fetchIpInfo(
            final String ip
    ) {

        return webClient
                .get()
                .uri(
                        ipInfoConfig.getBaseUrl() + "/lite/" + ip
                )
                .header(
                        "Authorization",
                        "Bearer " + ipInfoConfig.getToken()
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(rawResponse -> {

                    log.info(
                            "[IPINFO][RAW] response | ip={} => {}",
                            ip,
                            rawResponse
                    );

                    if (!StringUtils.hasText(rawResponse)) {
                        log.warn(
                                "[IPINFO] Empty response | ip={}",
                                ip
                        );

                        return null;
                    }

                    return parseIpInfo(
                            rawResponse,
                            ip
                    );
                })
                .onErrorResume(exception -> {

                    log.warn(
                            "[IPINFO] Failed to fetch IP information | ip={} | error={}",
                            ip,
                            exception.getMessage()
                    );

                    return Mono.empty();
                })
                .block();
    }

    /**
     * Parses the IPinfo Lite response.
     */
    private IpRecordDTO parseIpInfo(
            final String rawResponse,
            final String ip
    ) {

        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            return IpRecordDTO.builder()
                    .ip(getText(root, "ip", ip))
                    .asn(getText(root, "asn", null))
                    .asName(getText(root, "as_name", null))
                    .asDomain(getText(root, "as_domain", null))
                    .countryCode(getText(root, "country_code", null))
                    .country(getText(root, "country", null))
                    .continentCode(getText(root, "continent_code", null))
                    .continent(getText(root, "continent", null))
                    .build();

        } catch (Exception exception) {

            log.warn(
                    "[IPINFO] Failed to parse response | ip={} | error={}",
                    ip,
                    exception.getMessage()
            );

            return null;
        }
    }

    /**
     * Returns a text value from the JSON response.
     */
    private String getText(
            final JsonNode root,
            final String fieldName,
            final String defaultValue
    ) {

        String value = root.path(fieldName).asText(null);

        return StringUtils.hasText(value)
                ? value
                : defaultValue;
    }
}