package com.domainprobe.api.dto.dns;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DnsResponseDTO {

    private List<String> a;

    private List<String> aaaa;

    private List<String> cname;

    private List<String> mx;

    private List<String> ns;

    private List<String> txt;

    private List<String> caa;

    private List<String> soa;
}