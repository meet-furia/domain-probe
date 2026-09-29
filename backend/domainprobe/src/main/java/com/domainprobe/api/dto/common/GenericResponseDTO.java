package com.domainprobe.api.dto.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericResponseDTO<T> {

    @Builder.Default
    private boolean success = true;

    private String message;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private T data;
}
