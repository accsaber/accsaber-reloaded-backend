package com.accsaber.backend.model.dto.request.item;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class CreateTradeRequest {

    @NotNull
    private Long toUserId;

    @Valid
    @Size(max = 8)
    private List<TradeItem> offeredItems;

    @Valid
    @Size(max = 8)
    private List<TradeItem> requestedItems;

    private String message;

    @Data
    @NoArgsConstructor
    public static class TradeItem {

        @NotNull
        private UUID userItemLinkId;

        @Min(1)
        private long quantity = 1;
    }
}
