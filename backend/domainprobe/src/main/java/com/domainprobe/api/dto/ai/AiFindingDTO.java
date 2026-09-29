package com.domainprobe.api.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiFindingDTO {

    private String type;

    private String title;

    private String description;
}