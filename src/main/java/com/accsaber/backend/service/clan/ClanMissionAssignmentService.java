package com.accsaber.backend.service.clan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionProgressAxis;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.mission.UserMissionRepository;
import com.accsaber.backend.service.mission.MissionAssignmentContext;
import com.accsaber.backend.service.mission.MissionAssignmentService;
import com.accsaber.backend.service.mission.MissionBuilderService;
import com.accsaber.backend.service.mission.MissionPoolCache;
import com.accsaber.backend.service.mission.MissionRolloverService;
import com.accsaber.backend.service.mission.MissionRowFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ClanMissionAssignmentService {

    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final MissionTemplateRepository templateRepository;
    private final UserMissionRepository userMissionRepository;
    private final MissionAssignmentService missionAssignmentService;
    private final MissionBuilderService builderService;
    private final MissionRowFactory rowFactory;
    private final MissionRolloverService rolloverService;
    private final ClanLevelService levelService;
    private final ClanProperties clanProperties;
    private final TransactionTemplate transactionTemplate;
    private final Executor backfillExecutor;

    public ClanMissionAssignmentService(ClanRepository clanRepository, ClanMemberRepository memberRepository,
            MissionTemplateRepository templateRepository, UserMissionRepository userMissionRepository,
            MissionAssignmentService missionAssignmentService, MissionBuilderService builderService,
            MissionRowFactory rowFactory, MissionRolloverService rolloverService, ClanLevelService levelService,
            ClanProperties clanProperties, TransactionTemplate transactionTemplate,
            @Qualifier("backfillExecutor") Executor backfillExecutor) {
        this.clanRepository = clanRepository;
        this.memberRepository = memberRepository;
        this.templateRepository = templateRepository;
        this.userMissionRepository = userMissionRepository;
        this.missionAssignmentService = missionAssignmentService;
        this.builderService = builderService;
        this.rowFactory = rowFactory;
        this.rolloverService = rolloverService;
        this.levelService = levelService;
        this.clanProperties = clanProperties;
        this.transactionTemplate = transactionTemplate;
        this.backfillExecutor = backfillExecutor;
    }

    @Transactional
    public int expireStale() {
        return userMissionRepository.expireStaleClanMissions(Instant.now());
    }

    public List<UUID> clansWithoutMissions() {
        return userMissionRepository.findClanIdsWithoutCurrentMissions(Instant.now());
    }

    @Transactional
    public int fill(UUID clanId) {
        Clan clan = clanRepository.findByIdAndActiveTrue(clanId).orElse(null);
        List<MissionTemplate> templates = templateRepository.findByPoolAndActiveTrue(MissionPool.clan);
        if (clan == null || templates.isEmpty()) {
            return 0;
        }
        Instant expiresAt = rolloverService.nextRollover(MissionPool.clan, Instant.now());
        List<Long> members = memberRepository.findOpenUserIds(clanId);
        Random rng = new Random();
        Set<UUID> tried = new HashSet<>();
        int slots = levelService.capacityOf(clan, ClanCapacity.mission_slots);
        int opened = 0;
        while (opened < slots) {
            MissionTemplate template = builderService.weightedPickExcluding(templates, rng, tried);
            if (template == null) {
                break;
            }
            tried.add(template.getId());
            boolean open = template.isPerMember()
                    ? openPerMember(clan, template, members, expiresAt, rng)
                    : openPooled(clan, template, members.size(), expiresAt);
            if (open) {
                opened++;
            }
        }
        return opened;
    }

    public void fillMemberRowsAsync(Long userId) {
        backfillExecutor.execute(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> fillMemberRows(userId));
            } catch (Exception e) {
                log.warn("Clan mission fill on login failed for user {}: {}", userId, e.getMessage());
            }
        });
    }

    private void fillMemberRows(Long userId) {
        ClanMember membership = memberRepository.findOpenByUserId(userId).orElse(null);
        if (membership == null) {
            return;
        }
        Random rng = new Random();
        MissionPoolCache cache = emptyCache();
        for (UserMission parent : userMissionRepository.findPerMemberParentsMissing(membership.getClan().getId(),
                userId, Instant.now())) {
            UserMission row = buildMemberRow(userId, parent.getTemplate(), parent.getExpiresAt(), rng, cache);
            if (row != null) {
                row.setClan(parent.getClan());
                row.setParentMission(parent);
                userMissionRepository.save(row);
            }
        }
    }

    private double headcountShare(int openMembers) {
        return openMembers / clanProperties.getRosterReferenceMembers();
    }

    private boolean openPooled(Clan clan, MissionTemplate template, int openMembers, Instant expiresAt) {
        UserMission mission = rowFactory.build(null, template, null);
        double factor = headcountShare(openMembers);
        mission.setClan(clan);
        mission.setExpiresAt(expiresAt);
        mission.setTargetCount(scaled(mission.getTargetCount(), factor));
        mission.setTargetXp(scaled(mission.getTargetXp(), factor));
        if (template.getType().getAxis() == MissionProgressAxis.AP && mission.getTargetAp() != null) {
            mission.setTargetAp(mission.getTargetAp() * factor);
        }
        userMissionRepository.save(mission);
        return true;
    }

    private boolean openPerMember(Clan clan, MissionTemplate template, List<Long> members, Instant expiresAt,
            Random rng) {
        MissionPoolCache cache = emptyCache();
        List<UserMission> rows = new ArrayList<>();
        for (Long userId : members) {
            UserMission row = buildMemberRow(userId, template, expiresAt, rng, cache);
            if (row != null) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            return false;
        }
        int clears = (int) Math.ceil(clanProperties.getMissionClears() * headcountShare(members.size()));
        UserMission parent = userMissionRepository.save(UserMission.builder()
                .template(template)
                .pool(MissionPool.clan)
                .clan(clan)
                .targetCount(Math.min(rows.size(), clears))
                .itemReward(template.getAwardsItem())
                .expiresAt(expiresAt)
                .build());
        for (UserMission row : rows) {
            row.setClan(clan);
            row.setParentMission(parent);
        }
        userMissionRepository.saveAll(rows);
        return true;
    }

    private UserMission buildMemberRow(Long userId, MissionTemplate template, Instant expiresAt, Random rng,
            MissionPoolCache cache) {
        MissionAssignmentContext context = missionAssignmentService.contextFor(userId);
        if (context.activeCategories().isEmpty()) {
            return null;
        }
        UserMission row = builderService.pickAndBuild(context, List.of(template), expiresAt, MissionPool.clan, rng,
                new HashSet<>(), cache, null);
        if (row != null) {
            row.setItemReward(null);
        }
        return row;
    }

    private static MissionPoolCache emptyCache() {
        return new MissionPoolCache(List.of(), List.of(), List.of(), new ConcurrentHashMap<>());
    }

    private static Integer scaled(Integer value, double factor) {
        return value == null ? null : (int) Math.ceil(value * factor);
    }
}
