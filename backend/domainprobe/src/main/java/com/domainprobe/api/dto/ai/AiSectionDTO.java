package com.domainprobe.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSectionDTO {

    private String positive;

    private String negative;

    private String recommendation;
}