package com.accsaber.backend.model.dto.response.statistics;

import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class FirstEditionHolderResponse {

    private UUID itemId;
    private ItemResponse item;
    private UUID linkId;
    private Long serialNumber;
    @JsonUnwrapped
    private UserIdentity user;
}
