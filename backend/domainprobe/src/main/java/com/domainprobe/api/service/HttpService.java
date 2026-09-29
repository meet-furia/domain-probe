package com.domainprobe.api.service;

import com.domainprobe.api.dto.http.CookieDTO;
import com.domainprobe.api.dto.http.HttpResponseDTO;
import com.domainprobe.api.dto.http.SecurityHeadersDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class HttpService {

    private static final int MAX_REDIRECTS = 10;

    private final WebClient webClient;

    /* ============================================================
       HTTP DATA
       ============================================================ */

    /**
     * Fetches HTTP and website information for the supplied domain.
     *
     * <p>
     * Redirects are followed manually so that the complete
     * redirect chain can be returned.
     */
    public HttpResponseDTO fetchHttpData(final String domainName) {

        String currentUrl = "https://" + domainName;

        List<String> redirectChain = new ArrayList<>();

        long startTime = System.nanoTime();

        try {

            for (int redirectCount = 0;
                 redirectCount <= MAX_REDIRECTS;
                 redirectCount++) {

                HttpResponseDTO response =
                        fetchHttpResponse(
                                currentUrl,
                                startTime,
                                redirectChain
                        );

                if (!isRedirect(response.getStatusCode())) {
                    return response;
                }

                String redirectUrl =
                        resolveRedirectUrl(
                                currentUrl,
                                response.getRedirectLocation()
                        );

                if (!StringUtils.hasText(redirectUrl)) {
                    return response;
                }

                redirectChain.add(redirectUrl);

                currentUrl = redirectUrl;
            }

            log.warn(
                    "[HTTP] Maximum redirects reached | domain={}",
                    domainName
            );

            return HttpResponseDTO.builder()
                    .statusCode(null)
                    .finalUrl(currentUrl)
                    .redirectChain(redirectChain)
                    .responseTimeMs(getElapsedTime(startTime))
                    .securityHeaders(
                            SecurityHeadersDTO.builder().build()
                    )
                    .cookies(List.of())
                    .build();

        } catch (Exception exception) {

            log.warn(
                    "[HTTP] Failed to fetch website | domain={} | error={}",
                    domainName,
                    exception.getMessage()
            );

            return HttpResponseDTO.builder()
                    .statusCode(null)
                    .finalUrl(currentUrl)
                    .redirectChain(redirectChain)
                    .responseTimeMs(getElapsedTime(startTime))
                    .securityHeaders(
                            SecurityHeadersDTO.builder().build()
                    )
                    .cookies(List.of())
                    .build();
        }
    }

    /* ============================================================
       HTTP RESPONSE
       ============================================================ */

    /**
     * Fetches a single HTTP response without automatically
     * following redirects.
     */
    private HttpResponseDTO fetchHttpResponse(
            final String url,
            final long startTime,
            final List<String> redirectChain
    ) {

        return webClient
                .get()
                .uri(url)
                .accept(MediaType.ALL)
                .exchangeToMono(clientResponse -> {

                    HttpHeaders headers =
                            clientResponse
                                    .headers()
                                    .asHttpHeaders();

                    int statusCode =
                            clientResponse
                                    .statusCode()
                                    .value();

                    String location =
                            headers.getFirst(
                                    HttpHeaders.LOCATION
                            );

                    String contentType =
                            headers.getFirst(
                                    HttpHeaders.CONTENT_TYPE
                            );

                    Long contentLength =
                            parseContentLength(
                                    headers.getFirst(
                                            HttpHeaders.CONTENT_LENGTH
                                    )
                            );

                    String server =
                            headers.getFirst(
                                    HttpHeaders.SERVER
                            );

                    String contentEncoding =
                            headers.getFirst(
                                    HttpHeaders.CONTENT_ENCODING
                            );

                    SecurityHeadersDTO securityHeaders =
                            extractSecurityHeaders(headers);

                    List<CookieDTO> cookies =
                            extractCookies(headers);

                    log.info(
                            "[HTTP] Response | url={} | status={} | server={}",
                            url,
                            statusCode,
                            server
                    );

                    return clientResponse
                            .bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> {

                                String html = null;

                                if (StringUtils.hasText(contentType)
                                        && contentType.toLowerCase().contains("text/html")) {
                                    html = body;
                                }

                                return HttpResponseDTO.builder()
                                        .statusCode(statusCode)
                                        .finalUrl(url)
                                        .redirectLocation(location)
                                        .redirectChain(
                                                new ArrayList<>(
                                                        redirectChain
                                                )
                                        )
                                        .responseTimeMs(
                                                getElapsedTime(
                                                        startTime
                                                )
                                        )
                                        .server(server)
                                        .contentType(contentType)
                                        .contentLength(contentLength)
                                        .contentEncoding(contentEncoding)
                                        .securityHeaders(
                                                securityHeaders
                                        )
                                        .cookies(cookies)
                                        .html(html)
                                        .build();
                            });
                })
                .block();
    }

    /* ============================================================
       REDIRECT
       ============================================================ */

    /**
     * Checks whether the supplied HTTP status is a redirect.
     */
    private boolean isRedirect(
            final Integer statusCode
    ) {

        return statusCode != null
                && statusCode >= 300
                && statusCode < 400;
    }

    /**
     * Resolves a redirect location against the current URL.
     */
    private String resolveRedirectUrl(
            final String currentUrl,
            final String location
    ) {

        if (!StringUtils.hasText(location)) {
            return null;
        }

        try {

            return URI.create(currentUrl)
                    .resolve(location)
                    .toString();

        } catch (Exception exception) {

            log.warn(
                    "[HTTP] Failed to resolve redirect | currentUrl={} | location={}",
                    currentUrl,
                    location
            );

            return null;
        }
    }

    /* ============================================================
       SECURITY HEADERS
       ============================================================ */

    /**
     * Extracts common website security headers.
     */
    private SecurityHeadersDTO extractSecurityHeaders(
            final HttpHeaders headers
    ) {

        return SecurityHeadersDTO.builder()
                .contentSecurityPolicy(
                        headers.getFirst(
                                "Content-Security-Policy"
                        )
                )
                .xContentTypeOptions(
                        headers.getFirst(
                                "X-Content-Type-Options"
                        )
                )
                .xFrameOptions(
                        headers.getFirst(
                                "X-Frame-Options"
                        )
                )
                .referrerPolicy(
                        headers.getFirst(
                                "Referrer-Policy"
                        )
                )
                .permissionsPolicy(
                        headers.getFirst(
                                "Permissions-Policy"
                        )
                )
                .build();
    }

    /* ============================================================
       COOKIES
       ============================================================ */

    /**
     * Extracts cookies from Set-Cookie response headers.
     */
    private List<CookieDTO> extractCookies(
            final HttpHeaders headers
    ) {

        List<CookieDTO> cookies =
                new ArrayList<>();

        for (String setCookie :
                headers.getOrEmpty(
                        HttpHeaders.SET_COOKIE
                )) {

            CookieDTO cookie =
                    parseCookie(setCookie);

            if (cookie != null) {
                cookies.add(cookie);
            }
        }

        return cookies;
    }

    /**
     * Parses a Set-Cookie header.
     */
    private CookieDTO parseCookie(
            final String setCookie
    ) {

        if (!StringUtils.hasText(setCookie)) {
            return null;
        }

        String[] parts =
                setCookie.split(";");

        if (parts.length == 0) {
            return null;
        }

        String[] nameValue =
                parts[0].split("=", 2);

        if (nameValue.length != 2) {
            return null;
        }

        CookieDTO.CookieDTOBuilder cookie =
                CookieDTO.builder()
                        .name(nameValue[0].trim())
                        .value(nameValue[1].trim())
                        .secure(false)
                        .httpOnly(false);

        for (int i = 1; i < parts.length; i++) {

            String attribute =
                    parts[i].trim();

            String lowerAttribute =
                    attribute.toLowerCase();

            if (lowerAttribute.equals("secure")) {

                cookie.secure(true);

            } else if (lowerAttribute.equals("httponly")) {

                cookie.httpOnly(true);

            } else if (lowerAttribute.startsWith("samesite=")) {

                cookie.sameSite(
                        attribute.substring(
                                "SameSite=".length()
                        )
                );

            } else if (lowerAttribute.startsWith("domain=")) {

                cookie.domain(
                        attribute.substring(
                                "Domain=".length()
                        )
                );

            } else if (lowerAttribute.startsWith("path=")) {

                cookie.path(
                        attribute.substring(
                                "Path=".length()
                        )
                );

            } else if (lowerAttribute.startsWith("expires=")) {

                cookie.expires(
                        attribute.substring(
                                "Expires=".length()
                        )
                );
            }
        }

        return cookie.build();
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    /**
     * Parses the Content-Length header.
     */
    private Long parseContentLength(
            final String contentLength
    ) {

        if (!StringUtils.hasText(contentLength)) {
            return null;
        }

        try {

            return Long.parseLong(
                    contentLength
            );

        } catch (NumberFormatException exception) {

            return null;
        }
    }

    /**
     * Returns elapsed time in milliseconds.
     */
    private long getElapsedTime(
            final long startTime
    ) {

        return (
                System.nanoTime() - startTime
        ) / 1_000_000;
    }
}