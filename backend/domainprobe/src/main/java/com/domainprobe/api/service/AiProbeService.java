package com.domainprobe.api.service;

import com.domainprobe.api.config.GroqConfig;
import com.domainprobe.api.dto.ai.AiProbeResponseDTO;
import com.domainprobe.api.dto.domain.DomainReportDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiProbeService {

    private final WebClient webClient;
    private final GroqConfig groqConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ============================================================
       AI PROBE
       ============================================================ */

    /**
     * Generates an AI-powered analysis of the supplied
     * DomainProbe report.
     */
    public AiProbeResponseDTO generateProbe(
            final DomainReportDTO report
    ) {

        try {

            log.info(
                    "[AI PROBE] Generating AI probe | input={} | domainName={} | analyzedAt={}",
                    report.getInput(),
                    report.getDomainName(),
                    report.getAnalyzedAt()
            );

            String domainData =
                    objectMapper.writeValueAsString(report);

            String prompt =
                    buildPrompt(domainData);

            Map<String, Object> requestBody =
                    Map.of(
                            "model", "openai/gpt-oss-20b",
                            "messages", List.of(
                                    Map.of(
                                            "role", "user",
                                            "content", prompt
                                    )
                            ),
                            "temperature", 0.2,
                            "response_format", Map.of(
                                    "type", "json_object"
                            )
                    );

            log.info(
                    "[AI PROBE] Sending request to Groq | domain={} | model={}",
                    report.getDomainName(),
                    "openai/gpt-oss-20b"
            );

            String rawResponse =
                    webClient
                            .post()
                            .uri(
                                    groqConfig.getBaseUrl()
                                            + "/chat/completions"
                            )
                            .headers(headers ->
                                    headers.setBearerAuth(
                                            groqConfig.getApiKey()
                                    )
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            log.debug(
                    "[AI PROBE][RAW] Groq response | domain={} => {}",
                    report.getDomainName(),
                    rawResponse
            );

            if (!StringUtils.hasText(rawResponse)) {

                log.warn(
                        "[AI PROBE] Empty Groq response | domain={}",
                        report.getDomainName()
                );

                throw new RuntimeException(
                        "Empty response received from Groq"
                );
            }

            JsonNode root =
                    objectMapper.readTree(rawResponse);

            JsonNode choices =
                    root.path("choices");

            if (!choices.isArray() || choices.isEmpty()) {

                log.warn(
                        "[AI PROBE] Groq response contains no choices | domain={}",
                        report.getDomainName()
                );

                throw new RuntimeException(
                        "No AI response received from Groq"
                );
            }

            String responseText =
                    choices
                            .get(0)
                            .path("message")
                            .path("content")
                            .asText(null);

            if (!StringUtils.hasText(responseText)) {

                log.warn(
                        "[AI PROBE] Empty AI content received from Groq | domain={}",
                        report.getDomainName()
                );

                throw new RuntimeException(
                        "Empty AI content received from Groq"
                );
            }

            AiProbeResponseDTO aiProbeResponse =
                    objectMapper.readValue(
                            stripCodeFences(responseText),
                            AiProbeResponseDTO.class
                    );

            log.info(
                    "[AI PROBE] AI probe generated successfully | domain={}",
                    report.getDomainName()
            );

            return aiProbeResponse;

        } catch (Exception exception) {

            log.error(
                    "[AI PROBE] Failed to generate AI probe | domain={} | error={}",
                    report.getDomainName(),
                    exception.getMessage(),
                    exception
            );

            throw new RuntimeException(
                    "Failed to generate AI probe",
                    exception
            );
        }
    }

    /**
     * Removes markdown code fences in case the model adds them
     * despite the JSON response format.
     */
    private String stripCodeFences(
            final String text
    ) {

        return text
                .replaceAll(
                        "(?s)^\\s*```(?:json)?\\s*",
                        ""
                )
                .replaceAll(
                        "(?s)\\s*```\\s*$",
                        ""
                )
                .trim();
    }

    /* ============================================================
       AI PROMPT
       ============================================================ */

    /**
     * Builds the prompt used to generate the AI probe.
     */
    private String buildPrompt(
            final String domainData
    ) {

        return """
                You are DomainProbe's AI domain analysis assistant.
                
                Analyze the complete DomainProbe report provided below.
                
                Your job is to explain the domain analysis in simple,
                useful language that a normal website owner can understand.
                
                The DomainProbe report contains eight analysis areas:
                
                1. Domain & Registration
                2. DNS
                3. SSL & HTTPS
                4. HTTP & Website
                5. DNSSEC
                6. DMARC
                7. IP & Network
                8. Technology Detection
                
                IMPORTANT RULES:
                
                1. Use ONLY the information provided in the DomainProbe report.
                2. Do not invent facts.
                3. Do not make assumptions about missing information.
                4. Explain technical information in simple language.
                5. Do not simply repeat raw values from the report.
                6. Explain what the supplied information means.
                7. Analyze ALL EIGHT sections.
                8. Every section MUST contain a positive, negative, and recommendation.
                9. A positive should explain what is configured correctly or beneficial.
                10. A negative should explain an actual limitation, missing configuration, concern, or area that may need attention.
                11. Do NOT invent a negative finding just to fill the field.
                12. If there is no meaningful negative finding, say:
                    "No significant negative finding is visible from the supplied data."
                13. If there is no meaningful recommendation, say:
                    "No immediate action is required based on the supplied data."
                14. Do not treat missing data as a security problem.
                15. Do not assume that an empty section means the domain is insecure.
                16. Do not create a security score.
                17. Do not make claims that are not supported by the supplied data.
                18. Keep each explanation useful rather than extremely short.
                19. Avoid unnecessary technical jargon.
                20. Return ONLY valid JSON.
                21. Do not wrap the JSON in markdown code fences.
                22. Do not include any text before or after the JSON.
                
                STATUS RULES:
                
                GOOD:
                Use when the supplied data shows generally healthy configuration
                with no important issues requiring attention.
                
                FAIR:
                Use when the domain is generally functioning but the supplied
                data shows some limitations or areas that could be improved.
                
                NEEDS_ATTENTION:
                Use when the supplied data contains one or more important issues
                that should reasonably be reviewed by the website owner.
                
                IMPORTANT:
                The overall status should consider the complete report,
                not just one individual section.
                
                OUTPUT FORMAT:
                
                {
                  "status": "GOOD | FAIR | NEEDS_ATTENTION",
                  "summary": "A concise overall explanation of the domain, approximately 30-50 words.",
                
                  "domain": {
                    "positive": "Explain what is good about the domain registration and RDAP information.",
                    "negative": "Explain any meaningful issue or limitation in the domain registration data.",
                    "recommendation": "Give a practical recommendation based only on the supplied domain data."
                  },
                
                  "dns": {
                    "positive": "Explain what is good about the DNS configuration.",
                    "negative": "Explain any meaningful DNS issue or limitation.",
                    "recommendation": "Give a practical DNS recommendation based only on the supplied data."
                  },
                
                  "ssl": {
                    "positive": "Explain what is good about the SSL certificate, TLS and HTTPS configuration.",
                    "negative": "Explain any meaningful SSL or HTTPS issue or limitation.",
                    "recommendation": "Give a practical SSL or HTTPS recommendation."
                  },
                
                  "http": {
                    "positive": "Explain what is good about the HTTP and website response configuration.",
                    "negative": "Explain any meaningful HTTP, redirect, header, cookie or website issue.",
                    "recommendation": "Give a practical HTTP or website recommendation."
                  },
                
                  "dnssec": {
                    "positive": "Explain what is good about the DNSSEC configuration.",
                    "negative": "Explain any meaningful DNSSEC issue or limitation.",
                    "recommendation": "Give a practical DNSSEC recommendation."
                  },
                
                  "dmarc": {
                    "positive": "Explain what is good about the DMARC configuration.",
                    "negative": "Explain any meaningful DMARC issue or limitation.",
                    "recommendation": "Give a practical email authentication recommendation."
                  },
                
                  "ip": {
                    "positive": "Explain what is useful or positive about the IP and ASN information.",
                    "negative": "Explain any meaningful limitation or concern visible from the IP and ASN data.",
                    "recommendation": "Give a practical recommendation based only on the supplied IP and network data."
                  },
                
                  "technology": {
                    "positive": "Explain the useful technologies detected, if any.",
                    "negative": "Explain any meaningful limitation in the technology detection result.",
                    "recommendation": "Give a practical recommendation based only on the technology detection data."
                  },
                
                  "recommendation": "Give one concise overall recommendation based on the most important finding in the complete report."
                }
                
                Each positive, negative, and recommendation should normally be
                around 25-60 words.
                
                Do not repeat the same explanation across multiple sections.
                
                COMPLETE DOMAINPROBE REPORT:
                
                %s
                """.formatted(domainData);
    }
}