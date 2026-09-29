package com.domainprobe.api.dto.ip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpResponseDTO {

    private List<IpRecordDTO> records;
}