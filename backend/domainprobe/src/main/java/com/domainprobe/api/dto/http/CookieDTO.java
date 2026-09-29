package com.domainprobe.api.dto.http;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CookieDTO {

    private String name;

    private String value;

    private boolean secure;

    private boolean httpOnly;

    private String sameSite;

    private String domain;

    private String path;

    private String expires;
}