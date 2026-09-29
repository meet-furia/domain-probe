package com.domainprobe.api.dto.rdap;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RdapResponseDTO {

    private String objectClassName;
    private String handle;
    private String ldhName;

    private List<String> status;

    private JsonNode nameservers;
    private JsonNode secureDNS;
    private JsonNode events;
    private JsonNode entities;
}