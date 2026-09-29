package com.domainprobe.api.controller;

import com.domainprobe.api.dto.common.GenericResponseDTO;
import com.domainprobe.api.dto.domain.DomainReportDTO;
import com.domainprobe.api.service.DomainReportService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/domains")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Validated
public class DomainReportController {

    private final DomainReportService domainReportService;

    /**
     * Fetches a domain report with registration data from RDAP.
     */
    @GetMapping("/analyze")
    public GenericResponseDTO<DomainReportDTO> analyzeDomain(
            @RequestParam final @NotBlank String domainName
    ) {
        DomainReportDTO response = domainReportService.generateDomainReport(domainName);

        return GenericResponseDTO.<DomainReportDTO>builder()
                .success(true)
                .message("Domain report generated")
                .data(response)
                .build();
    }
}
