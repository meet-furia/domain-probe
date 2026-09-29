package com.domainprobe.api.dto.dnssec;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnssecResponseDTO {

    private boolean signed;
    private boolean validated;
    private List<DsRecordDTO> dsRecords;
    private List<DnskeyRecordDTO> dnskeyRecords;
    private List<String> rrsigRecords;
    private List<String> nsecRecords;
    private List<String> nsec3Records;
}