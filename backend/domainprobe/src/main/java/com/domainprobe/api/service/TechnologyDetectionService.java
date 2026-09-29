package com.domainprobe.api.service;

import com.domainprobe.api.dto.http.HttpResponseDTO;
import com.domainprobe.api.dto.technology.TechnologyDTO;
import com.domainprobe.api.dto.technology.TechnologyResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class TechnologyDetectionService {

    /* ============================================================
       TECHNOLOGY DETECTION
       ============================================================ */

    public TechnologyResponseDTO detectTechnologies(
            final HttpResponseDTO httpData
    ) {

        log.info("[TECHNOLOGY] Starting technology detection");

        if (httpData == null) {
            log.warn("[TECHNOLOGY] HTTP response data is null");

            return TechnologyResponseDTO.builder()
                    .technologies(List.of())
                    .build();
        }

        String html = httpData.getHtml();

        if (!StringUtils.hasText(html)) {
            log.warn("[TECHNOLOGY] No HTML content available");

            return TechnologyResponseDTO.builder()
                    .technologies(List.of())
                    .build();
        }

        List<TechnologyDTO> technologies = new ArrayList<>();

        String normalizedHtml =
                html.toLowerCase(Locale.ROOT);

        String server =
                httpData.getServer();

        /* ========================================================
           CDN / INFRASTRUCTURE
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "cloudflare",
                "cf-ray",
                "cf-cache-status",
                "challenges.cloudflare.com"
        ) || containsIgnoreCase(server, "cloudflare")) {

            technologies.add(
                    createTechnology(
                            "Cloudflare",
                            "CDN / Infrastructure",
                            "HIGH",
                            List.of(
                                    "Cloudflare indicators detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "fastly",
                "fastly.net"
        ) || containsIgnoreCase(server, "fastly")) {

            technologies.add(
                    createTechnology(
                            "Fastly",
                            "CDN / Infrastructure",
                            "HIGH",
                            List.of(
                                    "Fastly indicators detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "akamai",
                "akamaihd.net",
                "akamaized.net"
        ) || containsIgnoreCase(server, "akamai")) {

            technologies.add(
                    createTechnology(
                            "Akamai",
                            "CDN / Infrastructure",
                            "HIGH",
                            List.of(
                                    "Akamai indicators detected"
                            )
                    )
            );
        }

        /* ========================================================
           FRONTEND FRAMEWORKS
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "__next_data__",
                "/_next/static/",
                "next/router",
                "nextjs"
        )) {

            technologies.add(
                    createTechnology(
                            "Next.js",
                            "Frontend Framework",
                            "HIGH",
                            List.of(
                                    "Next.js fingerprints detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "reactdom",
                "__react",
                "_reactroot",
                "data-reactroot",
                "react.development.js",
                "react.production.min.js"
        )) {

            technologies.add(
                    createTechnology(
                            "React",
                            "Frontend Framework",
                            "HIGH",
                            List.of(
                                    "React fingerprints detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "ng-version",
                "angular.js",
                "angular.min.js",
                "@angular/",
                "ng-app",
                "ng-controller"
        )) {

            technologies.add(
                    createTechnology(
                            "Angular",
                            "Frontend Framework",
                            "HIGH",
                            List.of(
                                    "Angular fingerprints detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "__vue__",
                "data-v-",
                "vue.js",
                "vue.min.js",
                "vue.runtime"
        )) {

            technologies.add(
                    createTechnology(
                            "Vue.js",
                            "Frontend Framework",
                            "HIGH",
                            List.of(
                                    "Vue.js fingerprints detected"
                            )
                    )
            );
        }

        /* ========================================================
           CMS
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "/wp-content/",
                "/wp-includes/",
                "wp-json",
                "wordpress"
        )) {

            technologies.add(
                    createTechnology(
                            "WordPress",
                            "CMS",
                            "HIGH",
                            List.of(
                                    "WordPress paths or markers detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "cdn.shopify.com",
                "shopify.theme",
                "shopify_pay",
                "shopifyanalytics"
        )) {

            technologies.add(
                    createTechnology(
                            "Shopify",
                            "E-commerce / CMS",
                            "HIGH",
                            List.of(
                                    "Shopify fingerprints detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "wixstatic.com",
                "wixsite",
                "wix.com"
        )) {

            technologies.add(
                    createTechnology(
                            "Wix",
                            "Website Builder",
                            "HIGH",
                            List.of(
                                    "Wix fingerprints detected"
                            )
                    )
            );
        }

        /* ========================================================
           JAVASCRIPT LIBRARIES
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "jquery.min.js",
                "jquery.js",
                "jquery-",
                "jquery/"
        )) {

            technologies.add(
                    createTechnology(
                            "jQuery",
                            "JavaScript Library",
                            "HIGH",
                            List.of(
                                    "jQuery script detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "bootstrap.min.css",
                "bootstrap.css",
                "bootstrap.min.js",
                "bootstrap.bundle"
        )) {

            technologies.add(
                    createTechnology(
                            "Bootstrap",
                            "CSS / UI Framework",
                            "HIGH",
                            List.of(
                                    "Bootstrap assets detected"
                            )
                    )
            );
        }

        /* ========================================================
           ANALYTICS
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "google-analytics.com",
                "googletagmanager.com/gtag",
                "gtag(",
                "ga(",
                "googleanalytics"
        )) {

            technologies.add(
                    createTechnology(
                            "Google Analytics",
                            "Analytics",
                            "HIGH",
                            List.of(
                                    "Google Analytics tracking detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "googletagmanager.com",
                "google_tag_manager",
                "gtm.js"
        )) {

            technologies.add(
                    createTechnology(
                            "Google Tag Manager",
                            "Analytics / Tag Management",
                            "HIGH",
                            List.of(
                                    "Google Tag Manager detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "connect.facebook.net",
                "facebook pixel",
                "fbq("
        )) {

            technologies.add(
                    createTechnology(
                            "Meta Pixel",
                            "Analytics / Advertising",
                            "HIGH",
                            List.of(
                                    "Meta Pixel tracking detected"
                            )
                    )
            );
        }

        /* ========================================================
           PAYMENT PROVIDERS
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "js.stripe.com",
                "stripe.com/v3",
                "stripe.js",
                "stripe-elements"
        )) {

            technologies.add(
                    createTechnology(
                            "Stripe",
                            "Payment",
                            "HIGH",
                            List.of(
                                    "Stripe integration detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "razorpay.com",
                "checkout.razorpay.com",
                "razorpay.js"
        )) {

            technologies.add(
                    createTechnology(
                            "Razorpay",
                            "Payment",
                            "HIGH",
                            List.of(
                                    "Razorpay integration detected"
                            )
                    )
            );
        }

        /* ========================================================
           SECURITY SERVICES
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "google.com/recaptcha",
                "www.google.com/recaptcha",
                "g-recaptcha",
                "recaptcha/api.js"
        )) {

            technologies.add(
                    createTechnology(
                            "Google reCAPTCHA",
                            "Security",
                            "HIGH",
                            List.of(
                                    "Google reCAPTCHA detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "challenges.cloudflare.com",
                "cf-turnstile",
                "turnstile"
        )) {

            technologies.add(
                    createTechnology(
                            "Cloudflare Turnstile",
                            "Security",
                            "HIGH",
                            List.of(
                                    "Cloudflare Turnstile detected"
                            )
                    )
            );
        }

        /* ========================================================
           HOSTING
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "vercel.app",
                "vercel.com",
                "vercel.live"
        ) || containsIgnoreCase(server, "vercel")) {

            technologies.add(
                    createTechnology(
                            "Vercel",
                            "Hosting",
                            "MEDIUM",
                            List.of(
                                    "Vercel indicators detected"
                            )
                    )
            );
        }

        if (containsAny(
                normalizedHtml,
                "netlify.app",
                "netlify.com",
                "netlify"
        ) || containsIgnoreCase(server, "netlify")) {

            technologies.add(
                    createTechnology(
                            "Netlify",
                            "Hosting",
                            "MEDIUM",
                            List.of(
                                    "Netlify indicators detected"
                            )
                    )
            );
        }

        /* ========================================================
           WEB SERVERS
           ======================================================== */

        if (containsIgnoreCase(
                server,
                "nginx"
        )) {

            technologies.add(
                    createTechnology(
                            "Nginx",
                            "Web Server",
                            "HIGH",
                            List.of(
                                    "Server header: " + server
                            )
                    )
            );
        }

        if (containsIgnoreCase(
                server,
                "apache"
        )) {

            technologies.add(
                    createTechnology(
                            "Apache",
                            "Web Server",
                            "HIGH",
                            List.of(
                                    "Server header: " + server
                            )
                    )
            );
        }

        if (containsIgnoreCase(
                server,
                "litespeed"
        )) {

            technologies.add(
                    createTechnology(
                            "LiteSpeed",
                            "Web Server",
                            "HIGH",
                            List.of(
                                    "Server header: " + server
                            )
                    )
            );
        }

        /* ========================================================
           TECHNOLOGY-SPECIFIC META TAGS
           ======================================================== */

        if (containsAny(
                normalizedHtml,
                "<meta name=\"generator\" content=\"wordpress",
                "<meta name='generator' content='wordpress"
        )) {

            addIfNotPresent(
                    technologies,
                    createTechnology(
                            "WordPress",
                            "CMS",
                            "HIGH",
                            List.of(
                                    "WordPress generator meta tag detected"
                            )
                    )
            );
        }

        /* ========================================================
           DEDUPLICATION
           ======================================================== */

        technologies = deduplicateTechnologies(technologies);

        log.info(
                "[TECHNOLOGY] Detection completed | detected={}",
                technologies.size()
        );

        return TechnologyResponseDTO.builder()
                .technologies(technologies)
                .build();
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private TechnologyDTO createTechnology(
            final String name,
            final String category,
            final String confidence,
            final List<String> evidence
    ) {

        return TechnologyDTO.builder()
                .name(name)
                .category(category)
                .confidence(confidence)
                .evidence(evidence)
                .build();
    }

    private boolean containsAny(
            final String value,
            final String... searchValues
    ) {

        if (!StringUtils.hasText(value)) {
            return false;
        }

        for (String searchValue : searchValues) {

            if (StringUtils.hasText(searchValue)
                    && value.contains(
                    searchValue.toLowerCase(Locale.ROOT)
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean containsIgnoreCase(
            final String value,
            final String searchValue
    ) {

        return StringUtils.hasText(value)
                && StringUtils.hasText(searchValue)
                && value.toLowerCase(Locale.ROOT)
                .contains(searchValue.toLowerCase(Locale.ROOT));
    }

    private void addIfNotPresent(
            final List<TechnologyDTO> technologies,
            final TechnologyDTO technology
    ) {

        boolean alreadyExists = technologies.stream()
                .anyMatch(existing ->
                        existing.getName()
                                .equalsIgnoreCase(technology.getName())
                );

        if (!alreadyExists) {
            technologies.add(technology);
        }
    }

    private List<TechnologyDTO> deduplicateTechnologies(
            final List<TechnologyDTO> technologies
    ) {

        List<TechnologyDTO> uniqueTechnologies =
                new ArrayList<>();

        for (TechnologyDTO technology : technologies) {

            boolean alreadyExists = uniqueTechnologies.stream()
                    .anyMatch(existing ->
                            existing.getName()
                                    .equalsIgnoreCase(technology.getName())
                    );

            if (!alreadyExists) {
                uniqueTechnologies.add(technology);
            }
        }

        return uniqueTechnologies;
    }
}