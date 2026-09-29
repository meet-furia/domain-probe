package com.domainprobe.api.service;

import com.domainprobe.api.config.DnsConfig;
import com.domainprobe.api.dto.dnssec.DnskeyRecordDTO;
import com.domainprobe.api.dto.dnssec.DnssecResponseDTO;
import com.domainprobe.api.dto.dnssec.DsRecordDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DnssecService {

    private final WebClient webClient;
    private final DnsConfig dnsConfig;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    /* ============================================================
       DNSSEC DATA
       ============================================================ */

    /**
     * Fetches DNSSEC information for the supplied domain.
     * <p>
     * Queries:
     * <p>
     * 1. DS
     * Determines whether DNSSEC is enabled for the domain.
     * <p>
     * 2. A with DNSSEC enabled
     * Used to check whether the resolver validated the response.
     * <p>
     * 3. DNSKEY
     * Only queried when a DS record exists.
     */
    public DnssecResponseDTO fetchDnssecData(
            final String domainName
    ) {

        log.info(
                "[DNSSEC] Checking DNSSEC | domain={}",
                domainName
        );

        /*
         * ------------------------------------------------------------
         * 1. DS RECORDS
         * ------------------------------------------------------------
         *
         * DS is the primary indicator that the domain is
         * DNSSEC-enabled at the parent zone.
         */
        List<DsRecordDTO> dsRecords =
                fetchDsRecords(domainName);

        boolean signed =
                !dsRecords.isEmpty();

        log.info(
                "[DNSSEC] DS check completed | domain={} | signed={} | ds={}",
                domainName,
                signed,
                dsRecords.size()
        );

        /*
         * ------------------------------------------------------------
         * 2. DNSSEC VALIDATION
         * ------------------------------------------------------------
         *
         * Query an ordinary record with DO=true and inspect
         * Google's AD (Authenticated Data) flag.
         */
        String validationResponse =
                fetchDnsRecord(
                        domainName,
                        "A"
                );

        boolean validated =
                isValidated(validationResponse);

        /*
         * ------------------------------------------------------------
         * 3. DNSKEY
         * ------------------------------------------------------------
         *
         * DNSKEY is only useful when the domain has a DS record.
         *
         * This avoids an unnecessary DNSKEY query for unsigned
         * domains such as google.com / apple.com.
         */
        List<DnskeyRecordDTO> dnskeyRecords =
                signed
                        ? fetchDnskeyRecords(domainName)
                        : List.of();

        log.info(
                "[DNSSEC] Completed | domain={} | signed={} | validated={} | ds={} | dnskey={}",
                domainName,
                signed,
                validated,
                dsRecords.size(),
                dnskeyRecords.size()
        );

        return DnssecResponseDTO.builder()
                .signed(signed)
                .validated(validated)
                .dsRecords(dsRecords)
                .dnskeyRecords(dnskeyRecords)

                /*
                 * RRSIG, NSEC and NSEC3 are intentionally not queried.
                 *
                 * Keep these fields in the response because they are
                 * part of the existing DTO contract.
                 */
                .rrsigRecords(List.of())
                .nsecRecords(List.of())
                .nsec3Records(List.of())

                .build();
    }

    /* ============================================================
       DNSSEC VALIDATION
       ============================================================ */

    /**
     * Checks whether the DNS resolver successfully validated
     * the DNS response using DNSSEC.
     * <p>
     * Google Public DNS exposes this through the AD flag.
     */
    private boolean isValidated(
            final String rawResponse
    ) {

        if (!StringUtils.hasText(rawResponse)) {
            return false;
        }

        try {

            JsonNode root =
                    objectMapper.readTree(rawResponse);

            boolean authenticatedData =
                    root.path("AD").asBoolean(false);

            log.info(
                    "[DNSSEC] Validation status | AD={}",
                    authenticatedData
            );

            return authenticatedData;

        } catch (Exception exception) {

            log.warn(
                    "[DNSSEC] Failed to determine validation status | error={}",
                    exception.getMessage()
            );

            return false;
        }
    }

    /* ============================================================
       DS RECORDS
       ============================================================ */

    /**
     * Fetches DS records for the supplied domain.
     */
    private List<DsRecordDTO> fetchDsRecords(
            final String domainName
    ) {

        try {

            String rawResponse =
                    fetchDnsRecord(
                            domainName,
                            "DS"
                    );

            List<DsRecordDTO> records =
                    parseDsRecords(rawResponse);

            log.info(
                    "[DNSSEC] DS records fetched | domain={} | count={}",
                    domainName,
                    records.size()
            );

            return records;

        } catch (Exception exception) {

            log.warn(
                    "[DNSSEC] Failed to fetch DS records | domain={} | error={}",
                    domainName,
                    exception.getMessage()
            );

            return List.of();
        }
    }

    /**
     * Converts DS DNS answers into DTOs.
     * <p>
     * DS records may appear in either ANSWER or AUTHORITY.
     */
    private List<DsRecordDTO> parseDsRecords(
            final String rawResponse
    ) {

        List<DsRecordDTO> records =
                new ArrayList<>();

        if (!StringUtils.hasText(rawResponse)) {
            return records;
        }

        try {

            JsonNode root =
                    objectMapper.readTree(rawResponse);

            JsonNode answer =
                    root.path("Answer");

            JsonNode authority =
                    root.path("Authority");

            log.info(
                    "[DNSSEC] DS response sections | answer={} | authority={}",
                    answer.isArray() ? answer.size() : 0,
                    authority.isArray() ? authority.size() : 0
            );

            /*
             * DS records can be returned in either section depending
             * on the DNS response.
             */
            parseDsSection(
                    answer,
                    records
            );

            parseDsSection(
                    authority,
                    records
            );

        } catch (Exception exception) {

            log.warn(
                    "[DNSSEC] Failed to parse DS records | error={}",
                    exception.getMessage()
            );
        }

        return records;
    }

    private void parseDsSection(
            final JsonNode section,
            final List<DsRecordDTO> records
    ) {

        if (!section.isArray()) {
            return;
        }

        for (JsonNode record : section) {

            /*
             * DS record type = 43
             */
            if (record.path("type").asInt() != 43) {
                continue;
            }

            String data =
                    record.path("data").asText(null);

            if (!StringUtils.hasText(data)) {
                continue;
            }

            String[] parts =
                    data.trim().split("\\s+");

            /*
             * DS format:
             *
             * key tag
             * algorithm
             * digest type
             * digest
             */
            if (parts.length < 4) {
                continue;
            }

            records.add(
                    DsRecordDTO.builder()
                            .keyTag(parts[0])
                            .algorithm(parts[1])
                            .digestType(parts[2])
                            .digest(parts[3])
                            .build()
            );
        }
    }

    /* ============================================================
       DNSKEY RECORDS
       ============================================================ */

    /**
     * Fetches DNSKEY records for a DNSSEC-enabled domain.
     */
    private List<DnskeyRecordDTO> fetchDnskeyRecords(
            final String domainName
    ) {

        try {

            String rawResponse =
                    fetchDnsRecord(
                            domainName,
                            "DNSKEY"
                    );

            List<DnskeyRecordDTO> records =
                    parseDnskeyRecords(rawResponse);

            log.info(
                    "[DNSSEC] DNSKEY records fetched | domain={} | count={}",
                    domainName,
                    records.size()
            );

            return records;

        } catch (Exception exception) {

            log.warn(
                    "[DNSSEC] Failed to fetch DNSKEY records | domain={} | error={}",
                    domainName,
                    exception.getMessage()
            );

            return List.of();
        }
    }

    /**
     * Converts DNSKEY DNS answers into DTOs.
     * <p>
     * DNSKEY records normally appear in ANSWER.
     */
    private List<DnskeyRecordDTO> parseDnskeyRecords(
            final String rawResponse
    ) {

        List<DnskeyRecordDTO> records =
                new ArrayList<>();

        if (!StringUtils.hasText(rawResponse)) {
            return records;
        }

        try {

            JsonNode root =
                    objectMapper.readTree(rawResponse);

            JsonNode answer =
                    root.path("Answer");

            JsonNode authority =
                    root.path("Authority");

            log.info(
                    "[DNSSEC] DNSKEY response sections | answer={} | authority={}",
                    answer.isArray() ? answer.size() : 0,
                    authority.isArray() ? authority.size() : 0
            );

            parseDnskeySection(
                    answer,
                    records
            );

            parseDnskeySection(
                    authority,
                    records
            );

        } catch (Exception exception) {

            log.warn(
                    "[DNSSEC] Failed to parse DNSKEY records | error={}",
                    exception.getMessage()
            );
        }

        return records;
    }

    private void parseDnskeySection(
            final JsonNode section,
            final List<DnskeyRecordDTO> records
    ) {

        if (!section.isArray()) {
            return;
        }

        for (JsonNode record : section) {

            /*
             * DNSKEY record type = 48
             */
            if (record.path("type").asInt() != 48) {
                continue;
            }

            String data =
                    record.path("data").asText(null);

            if (!StringUtils.hasText(data)) {
                continue;
            }

            String[] parts =
                    data.trim().split("\\s+");

            /*
             * DNSKEY format:
             *
             * flags
             * protocol
             * algorithm
             * public key
             */
            if (parts.length < 4) {
                continue;
            }

            records.add(
                    DnskeyRecordDTO.builder()
                            .flags(parts[0])
                            .protocol(parts[1])
                            .algorithm(parts[2])
                            .publicKey(parts[3])
                            .build()
            );
        }
    }

    /* ============================================================
       DNS QUERY
       ============================================================ */

    /**
     * Performs a DNS-over-HTTPS query using the configured
     * DNS provider.
     * <p>
     * do=true requests DNSSEC-related data where applicable
     * and allows the resolver to return the AD validation flag.
     */
    private String fetchDnsRecord(
            final String domainName,
            final String recordType
    ) {

        String url =
                dnsConfig.getBaseUrl()
                        + "?name=" + domainName
                        + "&type=" + recordType
                        + "&do=true";

        log.info(
                "[DNSSEC] Querying DNS | domain={} | recordType={} | url={}",
                domainName,
                recordType,
                url
        );

        try {

            String response =
                    webClient
                            .get()
                            .uri(url)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            log.info(
                    "[DNSSEC] DNS query completed | domain={} | recordType={} | responseLength={}",
                    domainName,
                    recordType,
                    response == null ? 0 : response.length()
            );

            return response;

        } catch (Exception exception) {

            log.warn(
                    "[DNSSEC] DNS query failed | domain={} | recordType={} | error={}",
                    domainName,
                    recordType,
                    exception.getMessage()
            );

            return null;
        }
    }
}