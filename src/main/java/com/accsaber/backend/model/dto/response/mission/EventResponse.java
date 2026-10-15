package com.accsaber.backend.model.dto.response.mission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.dto.response.item.ItemResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventResponse {

    private UUID id;
    private String slug;
    private String title;
    private String description;
    private String backgroundUrl;
    private String iconUrl;
    private Instant startsAt;
    private Instant endsAt;
    private Integer bonusXp;
    private List<ItemResponse> bonusItems;
    private boolean active;
    private boolean live;
    private Integer currentWeek;
    private int totalWeeks;
}
