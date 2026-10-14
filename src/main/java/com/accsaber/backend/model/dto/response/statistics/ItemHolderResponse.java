package com.accsaber.backend.model.dto.response.statistics;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ItemHolderResponse {

    @JsonUnwrapped
    private UserIdentity user;
    private long quantity;
    private Long lowestSerial;
    private Instant acquiredAt;
    private List<String> modifiers;
    private Integer ranking;
    private boolean following;
}
