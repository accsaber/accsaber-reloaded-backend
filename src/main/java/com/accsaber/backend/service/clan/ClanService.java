package com.accsaber.backend.service.clan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.clan.CreateClanRequest;
import com.accsaber.backend.model.dto.request.clan.ModerateClanRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanRequest;
import com.accsaber.backend.model.dto.response.clan.ClanAuditEntryResponse;
import com.accsaber.backend.model.dto.response.clan.ClanResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.ClanAuditAction;
import com.accsaber.backend.model.entity.clan.ClanAuditEntry;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanLeaveReason;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.clan.ClanAuditEntryRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.clan.war.ClanWarService;
import com.accsaber.backend.util.Slugs;
import com.accsaber.backend.websocket.server.ClanFeedType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClanService {

    private static final Set<String> RESERVED_SLUGS = Set.of("join-requests", "levels", "seasons", "wars");


    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanAuditEntryRepository auditRepository;
    private final ClanRoster roster;
    private final ClanAccessService accessService;
    private final ClanLevelService levelService;
    private final ClanCosmeticService cosmeticService;
    private final ClanStandingService standingService;
    private final ClanAllianceService allianceService;
    private final ClanWarService warService;
    private final ClanNotifier notifier;
    private final ClanFeed feed;
    private final ClanRefCache refCache;
    private final ClanProperties clanProperties;

    private record ListExtras(Map<UUID, Long> memberCounts, Map<UUID, PlayerRef> founders,
            Map<UUID, PublicClanResponse> refs, Map<UUID, Double> earned, ClanLevelService.CapacityTable capacities) {
    }

    public Page<ClanResponse> list(String search, Pageable pageable) {
        Page<Clan> page = clanRepository.search(blankToNull(search), withSortExpressions(pageable));
        ListExtras extras = extrasFor(page.getContent());
        return page.map(clan -> toResponse(clan, extras));
    }

    public ClanResponse get(String slugOrId) {
        return toResponse(findActive(slugOrId));
    }

    @Transactional
    public ClanResponse create(Long playerId, CreateClanRequest request) {
        User founder = accessService.player(playerId);
        roster.assertCanJoin(founder.getId());
        String tag = request.getTag().toUpperCase(Locale.ROOT);
        String name = request.getName().trim();
        Clan clan = saveUnique(Clan.builder()
                .name(name)
                .tag(tag)
                .slug(slugFor(name, tag, null))
                .description(request.getDescription())
                .tagColor(normalizedColor(request.getTagColor()))
                .primaryColor(normalizedColor(request.getPrimaryColor()))
                .secondaryColor(normalizedColor(request.getSecondaryColor()))
                .build());
        roster.seat(clan, founder, ClanRole.founder);
        levelService.grantStartingItems(clan.getId());
        feed.clans(ClanFeedType.clan_founded, clan);
        return toResponse(clan);
    }

    @Transactional
    public ClanResponse update(UUID clanId, Long playerId, UpdateClanRequest request) {
        User actor = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        accessService.require(clanId, actor.getId(), ClanPermission.CUSTOMIZE);
        Map<String, Object> changes = applyChanges(clan, request);
        if (changes.isEmpty()) {
            return toResponse(clan);
        }
        saveUnique(clan);
        auditRepository.save(ClanAuditEntry.builder()
                .clan(clan).actor(actor).action(ClanAuditAction.profile_updated).details(changes).build());
        return toResponse(clan);
    }

    public void assertCanCustomize(UUID clanId, Long playerId) {
        accessService.require(clanId, accessService.player(playerId).getId(), ClanPermission.CUSTOMIZE);
    }

    @Transactional
    public ClanResponse setIcon(UUID clanId, Long playerId, String iconUrl) {
        User actor = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        accessService.require(clanId, actor.getId(), ClanPermission.CUSTOMIZE);
        clan.setIconUrl(iconUrl);
        saveUnique(clan);
        auditRepository.save(ClanAuditEntry.builder().clan(clan).actor(actor)
                .action(ClanAuditAction.profile_updated).details(iconChange(iconUrl)).build());
        return toResponse(clan);
    }

    @Transactional
    public ClanResponse moderate(UUID clanId, ModerateClanRequest request) {
        Clan clan = roster.lock(clanId);
        Map<String, Object> changes = applyChanges(clan, request.getChanges());
        if (request.isRemoveIcon() && clan.getIconUrl() != null) {
            clan.setIconUrl(null);
            changes.putAll(iconChange(null));
        }
        if (changes.isEmpty()) {
            return toResponse(clan);
        }
        saveUnique(clan);
        changes.put("reason", request.getReason());
        auditRepository.save(ClanAuditEntry.builder()
                .clan(clan).action(ClanAuditAction.profile_updated).details(changes).build());
        return toResponse(clan);
    }

    @Transactional
    public void disband(UUID clanId, Long playerId) {
        User actor = accessService.player(playerId);
        Clan clan = roster.lock(clanId);
        accessService.require(clanId, actor.getId(), ClanPermission.DISBAND);
        disband(clan, actor, null);
    }

    @Transactional
    public void disbandByStaff(UUID clanId, String reason) {
        Clan clan = roster.lock(clanId);
        List<Long> members = memberRepository.findOpenUserIds(clanId);
        disband(clan, null, reason);
        notifier.disbandedByStaff(clan, members, reason);
    }

    @Transactional
    public void disband(Clan clan, User actor, String reason) {
        clan.setActive(false);
        clanRepository.saveAndFlush(clan);
        if (actor != null && memberRepository.countByClan_IdAndLeftAtIsNull(clan.getId()) > 1) {
            memberRepository.findOpenByUserId(actor.getId())
                    .ifPresent(founder -> roster.close(founder, ClanLeaveReason.left));
        }
        roster.closeAll(clan.getId(), ClanLeaveReason.disbanded);
        allianceService.endAll(clan, actor);
        warService.forfeitAll(clan.getId());
        auditRepository.save(ClanAuditEntry.builder()
                .clan(clan).actor(actor).action(ClanAuditAction.disbanded)
                .details(reason == null ? null : Map.<String, Object>of("reason", reason)).build());
    }

    public Page<ClanAuditEntryResponse> audit(UUID clanId, Long playerId, Pageable pageable) {
        User viewer = accessService.player(playerId);
        accessService.require(clanId, viewer.getId(), ClanPermission.READ_AUDIT);
        return auditRepository.findPageByClanId(clanId, pageable).map(ClanAuditEntryResponse::of);
    }

    private Clan findActive(String slugOrId) {
        UUID id = Slugs.uuidOrNull(slugOrId);
        return (id != null ? clanRepository.findByIdAndActiveTrue(id) : clanRepository.findBySlugAndActiveTrue(slugOrId))
                .orElseThrow(() -> new ResourceNotFoundException("Clan", slugOrId));
    }

    private Map<String, Object> applyChanges(Clan clan, UpdateClanRequest request) {
        Map<String, Object> changes = new LinkedHashMap<>();
        apply(request.getName(), String::trim, clan.getName(), clan::setName, "name", changes);
        apply(request.getTag(), tag -> tag.toUpperCase(Locale.ROOT), clan.getTag(), clan::setTag, "tag", changes);
        if (changes.containsKey("name")) {
            clan.setSlug(slugFor(clan.getName(), clan.getTag(), clan.getSlug()));
        }
        apply(request.getDescription(), UnaryOperator.identity(), clan.getDescription(), clan::setDescription,
                "description", changes);
        apply(request.getTagColor(), ClanService::normalizedColor, clan.getTagColor(), clan::setTagColor, "tagColor",
                changes);
        apply(request.getPrimaryColor(), ClanService::normalizedColor, clan.getPrimaryColor(), clan::setPrimaryColor,
                "primaryColor", changes);
        apply(request.getSecondaryColor(), ClanService::normalizedColor, clan.getSecondaryColor(),
                clan::setSecondaryColor, "secondaryColor", changes);
        apply(request.getAcceptingRequests(), UnaryOperator.identity(), clan.isAcceptingRequests(),
                clan::setAcceptingRequests, "acceptingRequests", changes);
        return changes;
    }

    private static Map<String, Object> iconChange(String iconUrl) {
        return new LinkedHashMap<>(Map.of("icon", iconUrl == null ? "removed" : "updated"));
    }

    private static <T> void apply(T requested, UnaryOperator<T> normalize, T current, Consumer<T> setter, String key,
            Map<String, Object> changes) {
        if (requested == null) {
            return;
        }
        T value = normalize.apply(requested);
        if (!Objects.equals(value, current)) {
            setter.accept(value);
            changes.put(key, value == null ? "cleared" : value);
        }
    }

    private static String normalizedColor(String color) {
        return color == null || color.isBlank() ? null : color.toLowerCase(Locale.ROOT);
    }

    private String slugFor(String name, String tag, String currentSlug) {
        String base = Slugs.slugify(name);
        if (base.equals(currentSlug)) {
            return base;
        }
        if (base.isEmpty() || RESERVED_SLUGS.contains(base) || clanRepository.existsBySlugAndActiveTrue(base)) {
            base = base.isEmpty() ? tag.toLowerCase(Locale.ROOT) : base + "-" + tag.toLowerCase(Locale.ROOT);
        }
        return base;
    }

    private Clan saveUnique(Clan clan) {
        try {
            Clan saved = clanRepository.saveAndFlush(clan);
            refCache.refreshAfterCommit(saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An active clan already uses that name or tag");
        }
    }

    private ClanResponse toResponse(Clan clan) {
        return toResponse(clan, extrasFor(List.of(clan)));
    }

    private ListExtras extrasFor(List<Clan> clans) {
        if (clans.isEmpty()) {
            return new ListExtras(Map.of(), Map.of(), Map.of(), Map.of(), levelService.capacities());
        }
        List<UUID> ids = clans.stream().map(Clan::getId).toList();
        Map<UUID, Long> counts = memberRepository.countOpenByClanIds(ids).stream()
                .collect(Collectors.toMap(ClanMemberRepository.MemberCountView::getClanId,
                        ClanMemberRepository.MemberCountView::getMembers));
        Map<UUID, PlayerRef> founders = memberRepository.findOpenFounders(ids).stream()
                .collect(Collectors.toMap(m -> m.getClan().getId(), m -> PlayerRef.of(m.getUser()),
                        (first, second) -> first));
        return new ListExtras(counts, founders, cosmeticService.publicRefs(clans),
                standingService.earnedInCurrentSeason(ids), levelService.capacities());
    }

    private ClanResponse toResponse(Clan clan, ListExtras extras) {
        LevelResponse level = levelService.levelOf(clan);
        double standing = standingService.baseStanding(clan) + extras.earned().getOrDefault(clan.getId(), 0.0);
        return new ClanResponse(extras.refs().get(clan.getId()), clan.getDescription(), clan.isAcceptingRequests(),
                level, standing, extras.memberCounts().getOrDefault(clan.getId(), 0L),
                extras.capacities().at(level.getLevel(), ClanCapacity.member_slots),
                extras.founders().get(clan.getId()), clan.getCreatedAt());
    }

    private Pageable withSortExpressions(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            String expression = sortExpression(order.getProperty());
            if (expression != null) {
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        JpaSort.unsafe(order.getDirection(), expression));
            }
        }
        return pageable;
    }

    private String sortExpression(String property) {
        return switch (property) {
            case "level" -> "c.totalXp";
            case "members" -> "(SELECT COUNT(m) FROM ClanMember m WHERE m.clan = c AND m.leftAt IS NULL)";
            case "standing" -> "((c.rosterStrength + c.allyStrength) * " + clanProperties.getStandingPerSkill()
                    + " + COALESCE((SELECT ss.earned FROM ClanSeasonStanding ss WHERE ss.clan = c"
                    + " AND ss.season.closedAt IS NULL AND ss.season.startsAt <= CURRENT_TIMESTAMP"
                    + " AND ss.season.endsAt > CURRENT_TIMESTAMP), 0))";
            default -> null;
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
