package com.accsaber.backend.model.dto.response.mission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.accsaber.backend.model.entity.mission.Event;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventResponse {

    private UUID id;
    private String title;
    private String description;
    private String backgroundUrl;
    private String iconUrl;
    private Instant startsAt;
    private Instant endsAt;
    private Integer bonusXp;
    private List<BonusItem> bonusItems;
    private boolean active;
    private boolean live;
    private Integer currentWeek;
    private int totalWeeks;

    public record BonusItem(UUID id, String name) {
    }

    public static EventResponse from(Event e) {
        Instant now = Instant.now();
        return EventResponse.builder()
                .id(e.getId())
                .title(e.getTitle())
                .description(e.getDescription())
                .backgroundUrl(e.getBackgroundUrl())
                .iconUrl(e.getIconUrl())
                .startsAt(e.getStartsAt())
                .endsAt(e.getEndsAt())
                .bonusXp(e.getBonusXp())
                .bonusItems(e.getBonusItems().stream()
                        .map(i -> new BonusItem(i.getId(), i.getName()))
                        .toList())
                .active(e.isActive())
                .live(e.isLive(now))
                .currentWeek(e.currentWeek(now))
                .totalWeeks(e.totalWeeks())
                .build();
    }
}
