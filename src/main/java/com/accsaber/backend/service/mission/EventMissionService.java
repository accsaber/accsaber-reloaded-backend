package com.accsaber.backend.service.mission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
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
import com.accsaber.backend.model.dto.response.mission.EventProfileResponse;
import com.accsaber.backend.model.dto.response.mission.EventProgressResponse;
import com.accsaber.backend.model.dto.response.mission.EventResponse;
import com.accsaber.backend.model.dto.response.mission.MissionResponse;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.UserEventProfile;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.EventRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserEventProfileRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
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
    private final UserEventProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
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

        for (UUID eventId : templateRepository.findEventIdsWithUnlocksBetween(from, to)) {
            try {
                rolloutNewlyUnlocked(eventId);
            } catch (Exception e) {
                log.error("Event unlock rollout failed for event {}: {}", eventId, e.getMessage());
            }
        }
    }

    private void rolloutNewlyUnlocked(UUID eventId) {
        Event event = eventRepository.findByIdAndActiveTrue(eventId).orElse(null);
        if (event == null || !event.isLive(Instant.now())) {
            return;
        }
        List<MissionTemplate> templates = templateRepository.findActiveByEvent(eventId);
        if (templates.isEmpty()) {
            return;
        }
        List<Long> participants = profileRepository.findUserIdsByEvent(eventId);
        log.info("Rolling out newly unlocked '{}' missions for {} participants",
                event.getTitle(), participants.size());

        for (Long userId : participants) {
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

    @Transactional
    public EventProgressResponse begin(Long userId, UUID eventId) {
        Event event = activeEvent(eventId);
        if (!event.isLive(Instant.now())) {
            throw new ValidationException("Event is not live");
        }
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isBanned()) {
            throw new ValidationException("Banned users cannot join events");
        }
        List<MissionTemplate> templates = templateRepository.findActiveByEvent(eventId);
        UserEventProfile profile = profileRepository.findByEvent_IdAndUser_Id(eventId, userId).orElse(null);
        if (profile == null) {
            profile = UserEventProfile.builder()
                    .event(event)
                    .user(userRepository.getReferenceById(userId))
                    .build();
            recomputeProgression(profile, event, templates, Set.of());
            profile = profileRepository.save(profile);
        }
        ensureAssignments(userId, event, templates, profile);
        return buildProgress(userId, event, templates);
    }

    public void ensureForUser(Long userId) {
        Instant now = Instant.now();
        for (Event event : eventRepository.findLive(now)) {
            if (!profileRepository.existsByEvent_IdAndUser_Id(event.getId(), userId)) {
                continue;
            }
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
        if (!profileRepository.existsByEvent_IdAndUser_Id(eventId, userId)) {
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
        UserEventProfile profile = profileRepository.findByEvent_IdAndUser_Id(event.getId(), userId).orElse(null);
        if (profile == null) {
            return;
        }
        Set<UUID> completedIds = new HashSet<>(
                userMissionRepository.findCompletedTemplateIdsForEvent(userId, event.getId()));
        recomputeProgression(profile, event, templates, completedIds);
        profileRepository.save(profile);
        ensureAssignments(userId, event, templates, profile);
    }

    private void ensureAssignments(Long userId, Event event, List<MissionTemplate> templates,
            UserEventProfile profile) {
        Instant now = Instant.now();
        Map<UUID, List<MissionStatus>> existing = userMissionRepository
                .findTemplateStatusesByUserAndEvent(userId, event.getId()).stream()
                .collect(Collectors.groupingBy(
                        UserMissionRepository.TemplateStatusView::getTemplateId,
                        Collectors.mapping(UserMissionRepository.TemplateStatusView::getStatus,
                                Collectors.toList())));

        for (MissionTemplate template : templates) {
            if (template.weekOf(event) > profile.getUnlockedWeek()) {
                continue;
            }
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
        UserEventProfile profile = profileRepository.findByEvent_IdAndUser_Id(event.getId(), userId).orElse(null);
        if (profile == null) {
            return 0;
        }
        respawnIfRepeatable(userId, template, event);

        List<MissionTemplate> templates = templateRepository.findActiveByEvent(event.getId());
        Set<UUID> completedIds = new HashSet<>(
                userMissionRepository.findCompletedTemplateIdsForEvent(userId, event.getId()));
        int previousWeek = profile.getUnlockedWeek();
        boolean allComplete = recomputeProgression(profile, event, templates, completedIds);
        if (profile.getUnlockedWeek() > previousWeek) {
            ensureAssignments(userId, event, templates, profile);
        }
        int bonusXp = allComplete ? maybeAwardBonus(userId, profile, event) : 0;
        profileRepository.save(profile);
        return bonusXp;
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

    private boolean recomputeProgression(UserEventProfile profile, Event event,
            List<MissionTemplate> templates, Set<UUID> completedIds) {
        Instant now = Instant.now();
        Map<Integer, List<UUID>> idsByWeek = templates.stream()
                .collect(Collectors.groupingBy(t -> t.weekOf(event),
                        Collectors.mapping(MissionTemplate::getId, Collectors.toList())));
        Map<Integer, Instant> unlockByWeek = templates.stream()
                .collect(Collectors.toMap(t -> t.weekOf(event), t -> t.unlockInstant(event),
                        (a, b) -> a.isBefore(b) ? a : b));
        List<Integer> weeks = idsByWeek.keySet().stream().sorted().toList();

        int unlockedWeek = weeks.isEmpty() ? 1 : weeks.get(0);
        boolean allComplete = !weeks.isEmpty();
        for (int week : weeks) {
            if (unlockByWeek.get(week).isAfter(now)) {
                allComplete = false;
                break;
            }
            unlockedWeek = week;
            if (!completedIds.containsAll(idsByWeek.get(week))) {
                allComplete = false;
                break;
            }
        }
        profile.setUnlockedWeek(unlockedWeek);
        profile.setMissionsCompleted(completedIds.size());
        return allComplete;
    }

    private int maybeAwardBonus(Long userId, UserEventProfile profile, Event event) {
        if (profile.getBonusAwardedAt() != null) {
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
        Instant now = Instant.now();
        profile.setBonusXp(bonusXp);
        profile.setBonusAwardedAt(now);
        profile.setCompletedAt(now);
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
    public List<MissionResponse> getMissions(UUID eventId, Integer week) {
        Event event = activeEvent(eventId);
        return missionResponses(event, templatesForWeek(event, week));
    }

    private List<MissionResponse> missionResponses(Event event, List<MissionTemplate> templates) {
        MissionResponse.TargetContext ctx = buildTargetContext(templates);
        Instant now = Instant.now();
        return templates.stream()
                .map(t -> MissionResponse.fromTemplate(t, event, now, ctx))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventProgressResponse.EventMissionProgressResponse> getMissionsWithProgress(
            Long userId, UUID eventId, Integer week) {
        Event event = activeEvent(eventId);
        UserEventProfile profile = profileRepository.findByEvent_IdAndUser_Id(eventId, userId).orElse(null);
        return assembleMissionProgress(userId, event, templatesForWeek(event, week), profile);
    }

    @Transactional(readOnly = true)
    public EventProgressResponse getProgress(Long userId, UUID eventId) {
        Event event = activeEvent(eventId);
        return buildProgress(userId, event, templateRepository.findActiveByEvent(eventId));
    }

    private EventProgressResponse buildProgress(Long userId, Event event, List<MissionTemplate> templates) {
        UserEventProfile profile = profileRepository.findByEvent_IdAndUser_Id(event.getId(), userId).orElse(null);
        return EventProgressResponse.builder()
                .event(EventResponse.from(event))
                .profile(profile != null ? EventProfileResponse.from(profile) : null)
                .begun(profile != null)
                .missions(assembleMissionProgress(userId, event, templates, profile))
                .bonusAwarded(profile != null && profile.getBonusAwardedAt() != null)
                .build();
    }

    private List<EventProgressResponse.EventMissionProgressResponse> assembleMissionProgress(
            Long userId, Event event, List<MissionTemplate> templates, UserEventProfile profile) {
        MissionResponse.TargetContext ctx = buildTargetContext(templates);
        Map<UUID, List<UserMission>> byTemplate = userMissionRepository.findByUserAndEvent(userId, event.getId())
                .stream().collect(Collectors.groupingBy(m -> m.getTemplate().getId()));
        Instant now = Instant.now();
        int unlockedWeek = profile != null ? profile.getUnlockedWeek() : 0;

        return templates.stream().map(t -> {
            boolean weekLocked = t.weekOf(event) > unlockedWeek;
            List<UserMission> rows = weekLocked ? List.of() : byTemplate.getOrDefault(t.getId(), List.of());
            UserMission current = rows.stream()
                    .filter(m -> m.getStatus() == MissionStatus.active)
                    .reduce((a, b) -> b)
                    .orElseGet(() -> rows.stream()
                            .filter(m -> m.getStatus() == MissionStatus.completed)
                            .reduce((a, b) -> b).orElse(null));
            long completions = rows.stream().filter(m -> m.getStatus() == MissionStatus.completed).count();
            return EventProgressResponse.EventMissionProgressResponse.builder()
                    .mission(MissionResponse.fromTemplate(t, event, now, ctx))
                    .current(current != null ? MissionResponse.from(current) : null)
                    .completions(completions)
                    .completed(completions > 0)
                    .weekLocked(weekLocked)
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

    private MissionResponse.TargetContext buildTargetContext(List<MissionTemplate> templates) {
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
                playerIds.add(targets.playerIdAsLong());
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
        return new MissionResponse.TargetContext(categories, mapDifficulties, players);
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
                builder.targetPlayer(userRepository.getReferenceById(targets.playerIdAsLong()));
            }
            builder.targetAcc(targets.acc())
                    .targetAp(targets.ap())
                    .targetScore(targets.score())
                    .targetCount(targets.count())
                    .targetXp(targets.xp())
                    .targetThresholdAp(targets.thresholdAp())
                    .targetStreak(targets.streak())
                    .targetRankedBefore(targets.rankedBefore())
                    .targetCuratedOnly(targets.curatedOnly());
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
