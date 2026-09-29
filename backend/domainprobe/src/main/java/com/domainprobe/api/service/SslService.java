package com.domainprobe.api.service;

import com.domainprobe.api.config.SslConfig;
import com.domainprobe.api.dto.ssl.HttpsResponseDTO;
import com.domainprobe.api.dto.ssl.SslCertificateDTO;
import com.domainprobe.api.dto.ssl.SslResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetSocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SslService {

    private final WebClient webClient;

    private final SslConfig sslConfig;


    /* ============================================================
       SSL DATA
       ============================================================ */

    /**
     * Fetches SSL and HTTPS information for the supplied domain.
     */
    public SslResponseDTO fetchSslData(final String domainName) {

        SslCertificateDTO certificate =
                fetchCertificate(domainName);

        HttpsResponseDTO https =
                fetchHttpsData(domainName);

        return SslResponseDTO.builder()
                .certificate(certificate)
                .https(https)
                .build();
    }


    /* ============================================================
       CERTIFICATE
       ============================================================ */

    /**
     * Fetches the SSL certificate and TLS information
     * for the supplied domain.
     */
    private SslCertificateDTO fetchCertificate(final String domainName) {

        try {

            log.info(
                    "[SSL] Requesting certificate | domain={}",
                    domainName
            );

            SSLSocketFactory socketFactory =
                    (SSLSocketFactory) SSLSocketFactory.getDefault();

            try (SSLSocket socket =
                         (SSLSocket) socketFactory.createSocket()) {

                int timeout =
                        (int) sslConfig
                                .getTimeout()
                                .toMillis();

                socket.setSoTimeout(timeout);

                SSLParameters sslParameters =
                        socket.getSSLParameters();

                sslParameters.setEndpointIdentificationAlgorithm(
                        "HTTPS"
                );

                socket.setSSLParameters(sslParameters);

                socket.connect(
                        new InetSocketAddress(
                                domainName,
                                443
                        ),
                        timeout
                );

                socket.startHandshake();

                X509Certificate certificate =
                        getCertificate(socket);

                OffsetDateTime validFrom =
                        certificate
                                .getNotBefore()
                                .toInstant()
                                .atOffset(ZoneOffset.UTC);

                OffsetDateTime expiresAt =
                        certificate
                                .getNotAfter()
                                .toInstant()
                                .atOffset(ZoneOffset.UTC);

                long daysRemaining =
                        ChronoUnit.DAYS.between(
                                OffsetDateTime.now(ZoneOffset.UTC),
                                expiresAt
                        );

                boolean valid =
                        isCertificateValid(certificate);

                String tlsVersion =
                        socket
                                .getSession()
                                .getProtocol();

                List<String> domains =
                        readCertificateDomains(certificate);

                String issuer =
                        certificate
                                .getIssuerX500Principal()
                                .getName();

                String subject =
                        certificate
                                .getSubjectX500Principal()
                                .getName();

                log.info(
                        "[SSL] Certificate fetched | domain={} | issuer={} | expiresAt={} | tls={}",
                        domainName,
                        issuer,
                        expiresAt,
                        tlsVersion
                );

                return SslCertificateDTO.builder()
                        .enabled(true)
                        .valid(valid)
                        .issuer(issuer)
                        .subject(subject)
                        .validFrom(validFrom)
                        .expiresAt(expiresAt)
                        .daysRemaining(daysRemaining)
                        .tlsVersion(tlsVersion)
                        .domains(domains)
                        .build();
            }

        } catch (Exception exception) {

            log.warn(
                    "[SSL] Failed to fetch certificate | domain={} | error={}",
                    domainName,
                    exception.getMessage()
            );

            return SslCertificateDTO.builder()
                    .enabled(false)
                    .valid(false)
                    .daysRemaining(0)
                    .domains(List.of())
                    .build();
        }
    }


    /* ============================================================
       HTTPS
       ============================================================ */

    /**
     * Fetches HTTPS response information including
     * status code and HSTS.
     */
    private HttpsResponseDTO fetchHttpsData(
            final String domainName
    ) {

        try {

            log.info(
                    "[SSL] Checking HTTPS response | domain={}",
                    domainName
            );

            HttpsResponseDTO response =
                    webClient
                            .get()
                            .uri("https://" + domainName)
                            .accept(MediaType.ALL)
                            .exchangeToMono(clientResponse -> {

                                int statusCode =
                                        clientResponse
                                                .statusCode()
                                                .value();

                                String hsts =
                                        clientResponse
                                                .headers()
                                                .asHttpHeaders()
                                                .getFirst(
                                                        "Strict-Transport-Security"
                                                );

                                boolean hstsEnabled =
                                        StringUtils.hasText(hsts);

                                log.info(
                                        "[SSL] HTTPS response | domain={} | status={} | hsts={}",
                                        domainName,
                                        statusCode,
                                        hstsEnabled
                                );

                                return clientResponse
                                        .releaseBody()
                                        .thenReturn(
                                                HttpsResponseDTO.builder()
                                                        .statusCode(statusCode)
                                                        .hstsEnabled(hstsEnabled)
                                                        .build()
                                        );
                            })
                            .block(sslConfig.getTimeout());

            log.info(
                    "[SSL] HTTPS check completed | domain={} | response={}",
                    domainName,
                    response
            );

            return response;

        } catch (Exception exception) {

            log.warn(
                    "[SSL] Failed to fetch HTTPS response | domain={} | error={} | type={}",
                    domainName,
                    exception.getMessage(),
                    exception.getClass().getName()
            );

            return HttpsResponseDTO.builder()
                    .statusCode(null)
                    .hstsEnabled(false)
                    .httpRedirectsToHttps(false)
                    .build();
        }
    }

    /**
     * Checks whether the HTTP version of the domain
     * redirects to HTTPS.
     */
    private boolean checkHttpRedirect(
            final String domainName
    ) {

        try {

            log.info(
                    "[SSL] Checking HTTP redirect | domain={}",
                    domainName
            );

            Boolean redirectsToHttps =
                    webClient
                            .get()
                            .uri("http://" + domainName)
                            .exchangeToMono(clientResponse -> {

                                String location =
                                        clientResponse
                                                .headers()
                                                .asHttpHeaders()
                                                .getFirst(
                                                        HttpHeaders.LOCATION
                                                );

                                boolean redirect =
                                        clientResponse
                                                .statusCode()
                                                .is3xxRedirection()
                                                && StringUtils.hasText(location)
                                                && location
                                                .toLowerCase()
                                                .startsWith("https://");

                                log.info(
                                        "[SSL] HTTP response | domain={} | status={} | location={} | redirectsToHttps={}",
                                        domainName,
                                        clientResponse
                                                .statusCode()
                                                .value(),
                                        location,
                                        redirect
                                );

                                return clientResponse
                                        .releaseBody()
                                        .thenReturn(redirect);
                            })
                            .block(sslConfig.getTimeout());

            return Boolean.TRUE.equals(redirectsToHttps);

        } catch (Exception exception) {

            log.warn(
                    "[SSL] Failed to check HTTP redirect | domain={} | error={} | type={}",
                    domainName,
                    exception.getMessage(),
                    exception.getClass().getName()
            );

            return false;
        }
    }

    /* ============================================================
       CERTIFICATE HELPERS
       ============================================================ */

    /**
     * Reads the server certificate from the SSL session.
     */
    private X509Certificate getCertificate(
            final SSLSocket socket
    ) throws Exception {

        Certificate[] certificates =
                socket
                        .getSession()
                        .getPeerCertificates();

        if (certificates.length == 0) {
            throw new Exception(
                    "No SSL certificate received"
            );
        }

        return (X509Certificate) certificates[0];
    }


    /**
     * Checks whether the certificate is currently valid.
     */
    private boolean isCertificateValid(
            final X509Certificate certificate
    ) {

        try {

            certificate.checkValidity();

            return true;

        } catch (Exception exception) {

            return false;
        }
    }


    /**
     * Reads DNS names from the certificate SAN extension.
     */
    private List<String> readCertificateDomains(
            final X509Certificate certificate
    ) {

        List<String> domains =
                new ArrayList<>();

        try {

            var subjectAlternativeNames =
                    certificate
                            .getSubjectAlternativeNames();

            if (subjectAlternativeNames == null) {
                return domains;
            }

            for (List<?> entry : subjectAlternativeNames) {

                if (entry.size() < 2) {
                    continue;
                }

                Integer type =
                        (Integer) entry.get(0);

                Object value =
                        entry.get(1);

                if (type == 2
                        && value instanceof String) {

                    domains.add(
                            ((String) value)
                                    .toLowerCase()
                    );
                }
            }

        } catch (Exception exception) {

            log.warn(
                    "[SSL] Failed to read certificate domains | error={}",
                    exception.getMessage()
            );
        }

        return domains;
    }
}