package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.dto.response.mission.EventDetailResponse;
import com.accsaber.backend.model.dto.response.mission.EventMissionResponse;
import com.accsaber.backend.model.dto.response.mission.EventProgressResponse;
import com.accsaber.backend.model.dto.response.mission.EventResponse;
import com.accsaber.backend.model.dto.response.mission.UserMissionResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.UserEventBonus;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.EventRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserEventBonusRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.item.ItemService;
import com.accsaber.backend.service.item.LevelUpAwardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventMissionService {

    private final EventRepository eventRepository;
    private final MissionTemplateRepository templateRepository;
    private final UserMissionRepository userMissionRepository;
    private final UserEventBonusRepository bonusRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final ScoreRepository scoreRepository;
    private final LevelUpAwardService levelUpAwardService;
    private final ItemService itemService;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    @Qualifier("backfillExecutor")
    private Executor backfillExecutor;

    private volatile Instant lastUnlockSweep = Instant.now();

    @Scheduled(cron = "${accsaber.scheduler.event-unlock-cron:0 10 * * * *}")
    public void runUnlockSweep() {
        Instant to = Instant.now();
        Instant from = lastUnlockSweep;
        lastUnlockSweep = to;

        transactionTemplate.executeWithoutResult(status -> {
            int expired = userMissionRepository.expireEventMissions(to);
            if (expired > 0) {
                log.info("Expired {} event missions past their window", expired);
            }
        });

        Set<UUID> eventIds = new LinkedHashSet<>();
        for (Event event : eventRepository.findLive(to)) {
            if (event.getStartsAt().isAfter(from)) {
                eventIds.add(event.getId());
            }
        }
        eventIds.addAll(templateRepository.findEventIdsWithUnlocksBetween(from, to));

        for (UUID eventId : eventIds) {
            try {
                rolloutEvent(eventId);
            } catch (Exception e) {
                log.error("Event unlock rollout failed for event {}: {}", eventId, e.getMessage());
            }
        }
    }

    public void rolloutEvent(UUID eventId) {
        Event event = eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
        if (!event.isLive(Instant.now())) {
            throw new ValidationException("Event is not live");
        }
        List<MissionTemplate> templates = templateRepository.findActiveByEvent(eventId);
        if (templates.isEmpty()) {
            return;
        }
        List<Long> eligible = scoreRepository.findActivePlayerIdsWithAtLeastActiveScores(1);
        log.info("Rolling out event '{}' missions for {} users", event.getTitle(), eligible.size());

        for (Long userId : eligible) {
            backfillExecutor.execute(() -> {
                try {
                    transactionTemplate.executeWithoutResult(
                            status -> ensureForUserAndEvent(userId, event, templates));
                } catch (Exception e) {
                    log.error("Event mission assignment failed for user {} event {}: {}",
                            userId, eventId, e.getMessage());
                }
            });
        }
    }

    public void ensureForUser(Long userId) {
        Instant now = Instant.now();
        for (Event event : eventRepository.findLive(now)) {
            List<MissionTemplate> templates = templateRepository.findActiveByEvent(event.getId());
            if (templates.isEmpty()) {
                continue;
            }
            transactionTemplate.executeWithoutResult(
                    status -> ensureForUserAndEvent(userId, event, templates));
        }
    }

    public void ensureForUserAndEventId(Long userId, UUID eventId) {
        Event event = eventRepository.findByIdAndActiveTrue(eventId).orElse(null);
        if (event == null || !event.isLive(Instant.now())) {
            return;
        }
        List<MissionTemplate> templates = templateRepository.findActiveByEvent(eventId);
        if (templates.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(
                status -> ensureForUserAndEvent(userId, event, templates));
    }

    private void ensureForUserAndEvent(Long userId, Event event, List<MissionTemplate> templates) {
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null || user.isBanned()) {
            return;
        }
        Instant now = Instant.now();
        Map<UUID, List<MissionStatus>> existing = userMissionRepository
                .findTemplateStatusesByUserAndEvent(userId, event.getId()).stream()
                .collect(Collectors.groupingBy(
                        UserMissionRepository.TemplateStatusView::getTemplateId,
                        Collectors.mapping(UserMissionRepository.TemplateStatusView::getStatus,
                                Collectors.toList())));

        for (MissionTemplate template : templates) {
            if (!template.isOpenAt(event, now)) {
                continue;
            }
            List<MissionStatus> statuses = existing.getOrDefault(template.getId(), List.of());
            if (statuses.contains(MissionStatus.active) || statuses.contains(MissionStatus.voided)) {
                continue;
            }
            long completed = statuses.stream().filter(s -> s == MissionStatus.completed).count();
            if (completed >= allowedCompletions(template)) {
                continue;
            }
            userMissionRepository.save(buildUserMission(userId, template, event));
        }
    }

    @Transactional
    public int onEventMissionCompleted(UserMission mission, Long userId) {
        MissionTemplate template = mission.getTemplate();
        Event event = template.getEvent();
        if (event == null) {
            return 0;
        }
        respawnIfRepeatable(userId, template, event);
        return maybeAwardBonus(userId, event);
    }

    private void respawnIfRepeatable(Long userId, MissionTemplate template, Event event) {
        if (!template.isRepeatable() || !template.isOpenAt(event, Instant.now())) {
            return;
        }
        long completed = userMissionRepository.countByUser_IdAndTemplate_IdAndStatus(
                userId, template.getId(), MissionStatus.completed);
        if (completed >= allowedCompletions(template)) {
            return;
        }
        userMissionRepository.save(buildUserMission(userId, template, event));
    }

    private int maybeAwardBonus(Long userId, Event event) {
        if (bonusRepository.existsByEvent_IdAndUser_Id(event.getId(), userId)) {
            return 0;
        }
        long total = templateRepository.countByEvent_IdAndActiveTrue(event.getId());
        if (total == 0) {
            return 0;
        }
        long completed = userMissionRepository.countDistinctCompletedTemplatesForEvent(userId, event.getId());
        if (completed < total) {
            return 0;
        }

        int bonusXp = event.getBonusXp() != null ? event.getBonusXp() : 0;
        if (bonusXp > 0) {
            levelUpAwardService.addMissionXp(userId, BigDecimal.valueOf(bonusXp));
        }
        for (Item item : event.getBonusItems()) {
            try {
                itemService.awardSystem(userId, item.getId(), ItemSource.manual,
                        "event:" + event.getId(), "Event bonus: " + event.getTitle());
            } catch (Exception e) {
                log.warn("Failed to award event bonus item {} for event {}: {}",
                        item.getId(), event.getId(), e.getMessage());
            }
        }
        bonusRepository.save(UserEventBonus.builder()
                .event(event)
                .user(userRepository.getReferenceById(userId))
                .xpAwarded(bonusXp)
                .build());
        log.info("Awarded event completion bonus for event '{}' to user {}", event.getTitle(), userId);
        return bonusXp;
    }

    @Transactional(readOnly = true)
    public EventDetailResponse getDetail(UUID eventId) {
        Event event = activeEvent(eventId);
        return EventDetailResponse.builder()
                .event(EventResponse.from(event))
                .missions(missionResponses(event, templateRepository.findActiveByEvent(eventId)))
                .build();
    }

    @Transactional(readOnly = true)
    public List<EventMissionResponse> getMissions(UUID eventId, Integer week) {
        Event event = activeEvent(eventId);
        return missionResponses(event, templatesForWeek(event, week));
    }

    private List<EventMissionResponse> missionResponses(Event event, List<MissionTemplate> templates) {
        EventMissionResponse.TargetContext ctx = buildTargetContext(templates);
        Instant now = Instant.now();
        return templates.stream()
                .map(t -> EventMissionResponse.from(t, event, now, ctx))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventProgressResponse.EventMissionProgressResponse> getMissionsWithProgress(
            Long userId, UUID eventId, Integer week) {
        Event event = activeEvent(eventId);
        return assembleMissionProgress(userId, event, templatesForWeek(event, week));
    }

    @Transactional(readOnly = true)
    public EventProgressResponse getProgress(Long userId, UUID eventId) {
        Event event = activeEvent(eventId);
        List<MissionTemplate> templates = templateRepository.findActiveByEvent(eventId);
        return EventProgressResponse.builder()
                .event(EventResponse.from(event))
                .missions(assembleMissionProgress(userId, event, templates))
                .bonusAwarded(bonusRepository.existsByEvent_IdAndUser_Id(eventId, userId))
                .build();
    }

    private List<EventProgressResponse.EventMissionProgressResponse> assembleMissionProgress(
            Long userId, Event event, List<MissionTemplate> templates) {
        EventMissionResponse.TargetContext ctx = buildTargetContext(templates);
        Map<UUID, List<UserMission>> byTemplate = userMissionRepository.findByUserAndEvent(userId, event.getId())
                .stream().collect(Collectors.groupingBy(m -> m.getTemplate().getId()));
        Instant now = Instant.now();

        return templates.stream().map(t -> {
            List<UserMission> rows = byTemplate.getOrDefault(t.getId(), List.of());
            UserMission current = rows.stream()
                    .filter(m -> m.getStatus() == MissionStatus.active)
                    .reduce((a, b) -> b).orElse(null);
            long completions = rows.stream().filter(m -> m.getStatus() == MissionStatus.completed).count();
            return EventProgressResponse.EventMissionProgressResponse.builder()
                    .mission(EventMissionResponse.from(t, event, now, ctx))
                    .current(current != null ? UserMissionResponse.from(current) : null)
                    .completions(completions)
                    .completed(completions > 0)
                    .build();
        }).toList();
    }

    private Event activeEvent(UUID eventId) {
        return eventRepository.findByIdAndActiveTrue(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));
    }

    private List<MissionTemplate> templatesForWeek(Event event, Integer week) {
        List<MissionTemplate> templates = templateRepository.findActiveByEvent(event.getId());
        if (week == null) {
            return templates;
        }
        if (week < 1) {
            throw new ValidationException("week", "must be >= 1");
        }
        return templates.stream()
                .filter(t -> t.weekOf(event) == week)
                .toList();
    }

    private EventMissionResponse.TargetContext buildTargetContext(List<MissionTemplate> templates) {
        Set<UUID> categoryIds = new LinkedHashSet<>();
        Set<UUID> mapDifficultyIds = new LinkedHashSet<>();
        Set<Long> playerIds = new LinkedHashSet<>();
        for (MissionTemplate template : templates) {
            EventMissionTargets targets = template.getEventTargets();
            if (targets == null) {
                continue;
            }
            if (targets.categoryId() != null) {
                categoryIds.add(targets.categoryId());
            }
            if (targets.mapDifficultyId() != null) {
                mapDifficultyIds.add(targets.mapDifficultyId());
            }
            if (targets.playerId() != null) {
                playerIds.add(targets.playerId());
            }
        }
        Map<UUID, Category> categories = categoryIds.isEmpty() ? Map.of()
                : categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c));
        Map<UUID, MapDifficulty> mapDifficulties = mapDifficultyIds.isEmpty() ? Map.of()
                : mapDifficultyRepository
                        .findAllByIdInAndActiveTrueWithMapAndCategory(List.copyOf(mapDifficultyIds)).stream()
                        .collect(Collectors.toMap(MapDifficulty::getId, d -> d));
        Map<Long, User> players = playerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(playerIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        return new EventMissionResponse.TargetContext(categories, mapDifficulties, players);
    }

    private UserMission buildUserMission(Long userId, MissionTemplate template, Event event) {
        UserMission.UserMissionBuilder builder = UserMission.builder()
                .user(userRepository.getReferenceById(userId))
                .template(template)
                .pool(MissionPool.event)
                .xpReward(template.getFixedXp() != null ? template.getFixedXp() : 0)
                .itemReward(template.getAwardsItem())
                .expiresAt(template.closeInstant(event));
        EventMissionTargets targets = template.getEventTargets();
        if (targets != null) {
            if (targets.categoryId() != null) {
                builder.category(categoryRepository.getReferenceById(targets.categoryId()));
            }
            if (targets.mapDifficultyId() != null) {
                builder.targetMapDifficulty(mapDifficultyRepository.getReferenceById(targets.mapDifficultyId()));
            }
            if (targets.playerId() != null) {
                builder.targetPlayer(userRepository.getReferenceById(targets.playerId()));
            }
            builder.targetAcc(targets.acc())
                    .targetAp(targets.ap())
                    .targetScore(targets.score())
                    .targetCount(targets.count())
                    .targetXp(targets.xp())
                    .targetThresholdAp(targets.thresholdAp())
                    .targetStreak(targets.streak());
        }
        return builder.build();
    }

    private long allowedCompletions(MissionTemplate template) {
        if (!template.isRepeatable()) {
            return 1;
        }
        return template.getMaxCompletions() != null ? template.getMaxCompletions() : Long.MAX_VALUE;
    }
}
