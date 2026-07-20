package com.accsaber.backend.model.dto.response.item;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EssenceBalanceResponse {

    private long balance;
    private long reserved;
}
