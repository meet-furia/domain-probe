package com.domainprobe.api.dto.technology;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnologyDTO {

    private String name;

    private String category;

    private String confidence;

    private List<String> evidence;
}