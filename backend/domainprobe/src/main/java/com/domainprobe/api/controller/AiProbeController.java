package com.domainprobe.api.controller;

import com.domainprobe.api.dto.ai.AiProbeResponseDTO;
import com.domainprobe.api.dto.common.GenericResponseDTO;
import com.domainprobe.api.dto.domain.DomainReportDTO;
import com.domainprobe.api.service.AiProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiProbeController {

    private final AiProbeService aiProbeService;

    /**
     * Generates an AI-powered analysis of the supplied domain report.
     */
    @PostMapping("/probe")
    public GenericResponseDTO<AiProbeResponseDTO> generateProbe(
            @RequestBody final DomainReportDTO report
    ) {
        AiProbeResponseDTO response = aiProbeService.generateProbe(report);

        return GenericResponseDTO.<AiProbeResponseDTO>builder()
                .success(true)
                .message("AI probe generated")
                .data(response)
                .build();
    }
}