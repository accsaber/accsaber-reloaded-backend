package com.accsaber.backend.model.dto.request.market;

import java.util.UUID;

import com.accsaber.backend.validation.CleanText;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateListingRequest {

    @NotNull
    private UUID userItemLinkId;

    @Min(1)
    private long quantity = 1;

    @NotBlank
    @Size(max = 100)
    @CleanText
    private String title;

    @Size(max = 1000)
    @CleanText
    private String description;

    @Min(1)
    private Long startingBid;

    @Min(1)
    private Long buyoutPrice;

    @Min(1)
    private long minIncrement = 1;

    @Min(1)
    private Integer durationMinutes;
}
