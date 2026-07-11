package com.accsaber.backend.model.dto.response.mission;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.util.MissionDescriptionRenderer;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventMissionResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private String type;

    private Instant unlocksAt;
    private Instant completableUntil;
    private int week;
    private boolean unlocked;
    private boolean open;

    private boolean repeatable;
    private Integer maxCompletions;
    private Integer xp;
    private UUID awardsItemId;
    private String awardsItemName;

    private EventMissionTargets targets;
    private String categoryCode;
    private String targetMapSongName;
    private String targetPlayerName;

    public record TargetContext(
            Map<UUID, Category> categories,
            Map<UUID, MapDifficulty> mapDifficulties,
            Map<Long, User> players) {

        public Category category(EventMissionTargets targets) {
            return targets != null && targets.categoryId() != null
                    ? categories.get(targets.categoryId())
                    : null;
        }

        public MapDifficulty mapDifficulty(EventMissionTargets targets) {
            return targets != null && targets.mapDifficultyId() != null
                    ? mapDifficulties.get(targets.mapDifficultyId())
                    : null;
        }

        public User player(EventMissionTargets targets) {
            return targets != null && targets.playerId() != null
                    ? players.get(targets.playerId())
                    : null;
        }
    }

    public static EventMissionResponse from(MissionTemplate t, Event event, Instant now, TargetContext ctx) {
        Instant unlocksAt = t.unlockInstant(event);
        Instant until = t.closeInstant(event);
        EventMissionTargets targets = t.getEventTargets();
        Category category = ctx.category(targets);
        MapDifficulty mapDifficulty = ctx.mapDifficulty(targets);
        User player = ctx.player(targets);
        String mapName = MissionDescriptionRenderer.formatMap(mapDifficulty);
        return EventMissionResponse.builder()
                .id(t.getId())
                .code(t.getCode())
                .name(t.getName())
                .description(renderDescription(t, targets, category, mapName, player))
                .type(t.getType().name())
                .unlocksAt(unlocksAt)
                .completableUntil(until)
                .week(t.weekOf(event))
                .unlocked(!unlocksAt.isAfter(now))
                .open(t.isOpenAt(event, now))
                .repeatable(t.isRepeatable())
                .maxCompletions(t.getMaxCompletions())
                .xp(t.getFixedXp())
                .awardsItemId(t.getAwardsItem() != null ? t.getAwardsItem().getId() : null)
                .awardsItemName(t.getAwardsItem() != null ? t.getAwardsItem().getName() : null)
                .targets(targets)
                .categoryCode(category != null ? category.getCode() : null)
                .targetMapSongName(mapDifficulty != null && mapDifficulty.getMap() != null
                        ? mapDifficulty.getMap().getSongName() : null)
                .targetPlayerName(player != null ? player.getName() : null)
                .build();
    }

    private static String renderDescription(MissionTemplate t, EventMissionTargets targets,
            Category category, String mapName, User player) {
        return MissionDescriptionRenderer.render(t.getDescription(),
                new MissionDescriptionRenderer.Values(
                        targets != null ? targets.count() : null,
                        targets != null ? targets.xp() : null,
                        targets != null ? targets.acc() : null,
                        targets != null ? targets.ap() : null,
                        targets != null ? targets.score() : null,
                        targets != null ? targets.thresholdAp() : null,
                        targets != null ? targets.streak() : null,
                        mapName,
                        player != null ? player.getName() : null,
                        category != null ? category.getName() : null));
    }
}
