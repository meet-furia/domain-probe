package com.domainprobe.api.dto.rdap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdapDomainDTO {

    private String domainName;

    private Availability availability;

    private String registrar;

    private String registrarIanaId;

    private String registrarUrl;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime expiresAt;

    private long daysRemaining;
    
    private List<String> statuses;

    private List<String> nameservers;

    private boolean dnssecSigned;

    private List<String> events;

    public enum Availability {
        AVAILABLE,
        REGISTERED,
        RESTRICTED
    }
}