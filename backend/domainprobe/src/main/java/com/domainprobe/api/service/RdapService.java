package com.domainprobe.api.service;

import com.domainprobe.api.config.RdapConfig;
import com.domainprobe.api.dto.rdap.RdapDomainDTO;
import com.domainprobe.api.dto.rdap.RdapResponseDTO;
import com.domainprobe.api.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class RdapService {

    private final WebClient webClient;
    private final RdapConfig rdapConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();


    /* ============================================================
       DOMAIN DATA
       ============================================================ */

    /**
     * Fetches RDAP registration data for the supplied domain.
     * <p>
     * RDAP is an optional data source for DomainProbe.
     * If RDAP is unavailable, unsupported, or fails for any reason,
     * the rest of the domain analysis should continue normally.
     */
    public RdapDomainDTO fetchDomainData(final String domainName) {

        try {
            String rawResponse = fetchDomainDataRaw(domainName);

            /*
             * A null/empty response means RDAP could not provide
             * registration data.
             */
            if (!StringUtils.hasText(rawResponse)) {
                return buildUnavailableDomainResponse(domainName);
            }

            RdapResponseDTO response =
                    objectMapper.readValue(
                            rawResponse,
                            RdapResponseDTO.class
                    );

            return convertToDomainDTO(response, domainName);

        } catch (Exception exception) {

            /*
             * RDAP failure must not stop the complete DomainProbe
             * analysis. Log the problem and return an unavailable
             * RDAP response instead.
             */
            log.warn(
                    "[RDAP] Registration data unavailable | domain={} | reason={}",
                    domainName,
                    exception.getMessage()
            );

            return buildUnavailableDomainResponse(domainName);
        }
    }


    /**
     * RAW RDAP API call to fetch domain data.
     * <p>
     * Returns null when RDAP is unavailable or the TLD is not
     * supported by the IANA RDAP bootstrap registry.
     */
    private String fetchDomainDataRaw(final String domainName) {

        String topLevelDomain = domainName.substring(
                domainName.lastIndexOf('.') + 1
        );

        String baseUrl = resolveRdapBaseUrl(topLevelDomain);

        /*
         * No RDAP server is available for this TLD.
         * Return null instead of throwing an exception so the
         * complete domain analysis can continue.
         */
        if (!StringUtils.hasText(baseUrl)) {
            log.warn(
                    "[RDAP] No RDAP server found | domain={} | tld={}",
                    domainName,
                    topLevelDomain
            );

            return null;
        }

        String url = baseUrl + "domain/" + domainName;

        log.info(
                "[RDAP] Requesting domain={} | url={}",
                domainName,
                url
        );

        try {

            String rawResponse = webClient
                    .get()
                    .uri(url)
                    .accept(
                            MediaType.parseMediaType("application/rdap+json"),
                            MediaType.APPLICATION_JSON
                    )
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info(
                    "[RDAP][RAW] Domain response | domain={} => {}",
                    domainName,
                    rawResponse
            );

            return rawResponse;

        } catch (WebClientResponseException.NotFound exception) {

            /*
             * RDAP 404 means the domain was not found in the
             * RDAP registry. Keep the existing behavior where
             * this is interpreted as an available domain.
             */
            log.info(
                    "[RDAP] 404 - domain not found | domain={} | url={}",
                    domainName,
                    url
            );

            return null;

        } catch (Exception exception) {

            /*
             * RDAP itself failed. Do not allow this to stop the
             * complete DomainProbe report.
             */
            log.warn(
                    "[RDAP] Request failed | domain={} | reason={}",
                    domainName,
                    exception.getMessage()
            );

            return null;
        }
    }


    /* ============================================================
       RDAP BOOTSTRAP
       ============================================================ */

    /**
     * Resolves the RDAP server responsible for the supplied TLD.
     * <p>
     * Returns null when the TLD is not present in the IANA
     * RDAP bootstrap registry.
     */
    private String resolveRdapBaseUrl(final String topLevelDomain) {

        try {

            Map<String, String> services = loadRdapServices();

            String baseUrl = services.get(
                    topLevelDomain.toLowerCase(Locale.ROOT)
            );

            if (baseUrl == null) {

                log.warn(
                        "[RDAP] TLD not supported by RDAP bootstrap | tld={}",
                        topLevelDomain
                );

                return null;
            }

            return baseUrl;

        } catch (Exception exception) {

            /*
             * Failure to load/parse the bootstrap registry should
             * not stop the overall DomainProbe analysis.
             */
            log.warn(
                    "[RDAP] Could not resolve RDAP server | tld={} | reason={}",
                    topLevelDomain,
                    exception.getMessage()
            );

            return null;
        }
    }


    /**
     * Loads RDAP bootstrap data from IANA.
     * <p>
     * Any failure is propagated to resolveRdapBaseUrl(), where
     * it is converted into a non-fatal RDAP failure.
     */
    private Map<String, String> loadRdapServices() {

        try {

            String rawResponse = webClient
                    .get()
                    .uri(rdapConfig.getBootstrapUrl())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (!StringUtils.hasText(rawResponse)) {

                throw new ExternalApiException(
                        "Empty response received from IANA RDAP bootstrap"
                );
            }

            return parseBootstrap(rawResponse);

        } catch (Exception exception) {

            throw new ExternalApiException(
                    "Failed to fetch IANA RDAP bootstrap",
                    exception
            );
        }
    }


    /**
     * Converts the IANA bootstrap response into a
     * TLD-to-RDAP-server mapping.
     */
    private Map<String, String> parseBootstrap(
            final String rawResponse
    ) {

        try {

            Map<String, String> services = new HashMap<>();

            JsonNode root = objectMapper.readTree(rawResponse);

            for (JsonNode service : root.path("services")) {

                JsonNode topLevelDomains = service.path(0);
                JsonNode urls = service.path(1);

                if (!urls.isArray() || urls.isEmpty()) {
                    continue;
                }

                String baseUrl = urls.get(0).asText();

                if (!baseUrl.endsWith("/")) {
                    baseUrl += "/";
                }

                for (JsonNode topLevelDomain : topLevelDomains) {

                    services.put(
                            topLevelDomain
                                    .asText()
                                    .toLowerCase(Locale.ROOT),
                            baseUrl
                    );
                }
            }

            if (services.isEmpty()) {

                throw new ExternalApiException(
                        "IANA RDAP bootstrap did not contain any services"
                );
            }

            return Map.copyOf(services);

        } catch (ExternalApiException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new ExternalApiException(
                    "Failed to parse IANA RDAP bootstrap response",
                    exception
            );
        }
    }


    /* ============================================================
       DOMAIN RESPONSE
       ============================================================ */

    /**
     * Converts RDAP response DTO into DomainProbe's domain DTO.
     */
    private RdapDomainDTO convertToDomainDTO(
            final RdapResponseDTO response,
            final String domainName
    ) {

        List<String> statuses = response.getStatus() != null
                ? response.getStatus()
                : List.of();

        List<String> nameservers = readNameservers(response);

        List<String> events = new ArrayList<>();

        EventDates eventDates = readEvents(
                response,
                events
        );

        RegistrarDetails registrar = readRegistrar(response);

        boolean dnssecSigned =
                response.getSecureDNS() != null
                        && response
                        .getSecureDNS()
                        .path("delegationSigned")
                        .asBoolean(false);

        return RdapDomainDTO.builder()
                .domainName(
                        StringUtils.hasText(response.getLdhName())
                                ? response.getLdhName()
                                : domainName
                )
                .availability(
                        determineAvailability(statuses)
                )
                .registrar(registrar.name)
                .registrarIanaId(registrar.ianaId)
                .registrarUrl(registrar.url)
                .createdAt(eventDates.createdAt)
                .updatedAt(eventDates.updatedAt)
                .expiresAt(eventDates.expiresAt)
                .daysRemaining(
                        calculateDaysRemaining(eventDates.expiresAt)
                )
                .statuses(statuses)
                .nameservers(nameservers)
                .dnssecSigned(dnssecSigned)
                .events(events)
                .build();
    }


    /**
     * Creates an RDAP response when registration data
     * cannot be retrieved.
     * <p>
     * This is intentionally different from AVAILABLE.
     * <p>
     * AVAILABLE means RDAP successfully responded with
     * a 404 and the domain was not found.
     * <p>
     * UNAVAILABLE means RDAP data could not be retrieved.
     */
    private RdapDomainDTO buildUnavailableDomainResponse(
            final String domainName
    ) {

        return RdapDomainDTO.builder()
                .domainName(domainName)
                .availability(null)
                .statuses(List.of())
                .nameservers(List.of())
                .events(List.of())
                .build();
    }


    /**
     * Determines domain availability from RDAP statuses.
     */
    private RdapDomainDTO.Availability determineAvailability(
            final List<String> statuses
    ) {

        boolean restricted = statuses.stream()
                .map(String::toLowerCase)
                .anyMatch(status ->
                        status.contains("reserved")
                                || status.contains("restricted")
                );

        return restricted
                ? RdapDomainDTO.Availability.RESTRICTED
                : RdapDomainDTO.Availability.REGISTERED;
    }


    /* ============================================================
       NAMESERVERS
       ============================================================ */

    /**
     * Converts RDAP nameservers into a simple list of hostnames.
     */
    private List<String> readNameservers(
            final RdapResponseDTO response
    ) {

        List<String> nameservers = new ArrayList<>();

        JsonNode nameserversNode = response.getNameservers();

        if (nameserversNode == null
                || !nameserversNode.isArray()) {

            return nameservers;
        }

        for (JsonNode nameserver : nameserversNode) {

            String name = nameserver
                    .path("ldhName")
                    .asText(null);

            if (StringUtils.hasText(name)) {

                nameservers.add(
                        name.toLowerCase(Locale.ROOT)
                );
            }
        }

        return nameservers;
    }


    /* ============================================================
       REGISTRAR
       ============================================================ */

    /**
     * Extracts registrar information from the RDAP entities.
     */
    private RegistrarDetails readRegistrar(
            final RdapResponseDTO response
    ) {

        JsonNode entities = response.getEntities();

        if (entities == null || !entities.isArray()) {

            return new RegistrarDetails(
                    null,
                    null,
                    null
            );
        }

        for (JsonNode entity : entities) {

            if (!hasRegistrarRole(entity.path("roles"))) {
                continue;
            }

            return new RegistrarDetails(
                    readRegistrarName(entity),
                    readRegistrarIanaId(entity),
                    readRegistrarUrl(entity)
            );
        }

        return new RegistrarDetails(
                null,
                null,
                null
        );
    }


    /**
     * Checks whether an RDAP entity represents a registrar.
     */
    private boolean hasRegistrarRole(
            final JsonNode roles
    ) {

        if (roles == null || !roles.isArray()) {
            return false;
        }

        for (JsonNode role : roles) {

            if ("registrar".equalsIgnoreCase(
                    role.asText()
            )) {

                return true;
            }
        }

        return false;
    }


    /**
     * Reads the registrar name from the RDAP vCard.
     */
    private String readRegistrarName(
            final JsonNode entity
    ) {

        JsonNode vcardArray = entity.path("vcardArray");

        if (!vcardArray.isArray()
                || vcardArray.size() < 2) {

            return null;
        }

        JsonNode vcard = vcardArray.get(1);

        if (!vcard.isArray()) {
            return null;
        }

        for (JsonNode item : vcard) {

            if (item.isArray()
                    && item.size() > 3
                    && "fn".equalsIgnoreCase(
                    item.get(0).asText()
            )) {

                return item.get(3).asText(null);
            }
        }

        return null;
    }


    /**
     * Reads the registrar IANA ID.
     */
    private String readRegistrarIanaId(
            final JsonNode entity
    ) {

        JsonNode publicIds = entity.path("publicIds");

        if (!publicIds.isArray()) {
            return null;
        }

        for (JsonNode publicId : publicIds) {

            if ("IANA Registrar ID".equalsIgnoreCase(
                    publicId.path("type").asText()
            )) {

                return publicId
                        .path("identifier")
                        .asText(null);
            }
        }

        return null;
    }


    /**
     * Reads the registrar URL from RDAP links.
     */
    private String readRegistrarUrl(
            final JsonNode entity
    ) {

        JsonNode links = entity.path("links");

        if (!links.isArray()) {
            return null;
        }

        for (JsonNode link : links) {

            if ("about".equalsIgnoreCase(
                    link.path("rel").asText()
            )) {

                return link
                        .path("href")
                        .asText(null);
            }
        }

        return null;
    }


    /* ============================================================
       EVENTS
       ============================================================ */

    /**
     * Converts RDAP events into DomainProbe event dates.
     */
    private EventDates readEvents(
            final RdapResponseDTO response,
            final List<String> descriptions
    ) {

        OffsetDateTime createdAt = null;
        OffsetDateTime updatedAt = null;
        OffsetDateTime expiresAt = null;

        JsonNode events = response.getEvents();

        if (events == null || !events.isArray()) {

            return new EventDates(
                    null,
                    null,
                    null
            );
        }

        for (JsonNode event : events) {

            String action = event
                    .path("eventAction")
                    .asText(null);

            String date = event
                    .path("eventDate")
                    .asText(null);

            if (StringUtils.hasText(action)) {

                descriptions.add(
                        action
                                + (StringUtils.hasText(date)
                                ? ": " + date
                                : "")
                );
            }

            OffsetDateTime eventDate = parseDate(date);

            if (eventDate == null) {
                continue;
            }

            if ("registration".equalsIgnoreCase(action)) {

                createdAt = eventDate;

            } else if ("last changed".equalsIgnoreCase(action)) {

                updatedAt = eventDate;

            } else if ("expiration".equalsIgnoreCase(action)) {

                expiresAt = eventDate;
            }
        }

        return new EventDates(
                createdAt,
                updatedAt,
                expiresAt
        );
    }


    /**
     * Parses an RDAP event date.
     */
    private OffsetDateTime parseDate(
            final String value
    ) {

        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {

            return OffsetDateTime.parse(value);

        } catch (DateTimeParseException exception) {

            return null;
        }
    }


    /**
     * Calculates the number of days remaining until domain expiration.
     */
    private long calculateDaysRemaining(
            final OffsetDateTime expiresAt
    ) {

        if (expiresAt == null) {
            return 0;
        }

        return Math.max(
                0,
                ChronoUnit.DAYS.between(
                        LocalDate.now(),
                        expiresAt.toLocalDate()
                )
        );
    }


    /* ============================================================
       INTERNAL DATA
       ============================================================ */

    /**
     * Holds registrar information extracted from RDAP.
     */
    private static class RegistrarDetails {

        private final String name;
        private final String ianaId;
        private final String url;

        private RegistrarDetails(
                final String name,
                final String ianaId,
                final String url
        ) {

            this.name = name;
            this.ianaId = ianaId;
            this.url = url;
        }
    }


    /**
     * Holds RDAP event dates extracted from RDAP.
     */
    private static class EventDates {

        private final OffsetDateTime createdAt;
        private final OffsetDateTime updatedAt;
        private final OffsetDateTime expiresAt;

        private EventDates(
                final OffsetDateTime createdAt,
                final OffsetDateTime updatedAt,
                final OffsetDateTime expiresAt
        ) {

            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.expiresAt = expiresAt;
        }
    }
}