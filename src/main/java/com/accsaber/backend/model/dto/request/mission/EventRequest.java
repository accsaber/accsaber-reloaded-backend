package com.accsaber.backend.model.dto.request.mission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class EventRequest {

    private String title;

    private String slug;

    private String description;

    private String backgroundUrl;

    private String iconUrl;

    private Instant startsAt;

    private Instant endsAt;

    @PositiveOrZero
    private Integer bonusXp;

    private List<UUID> bonusItemIds;

    private Boolean active;
}
