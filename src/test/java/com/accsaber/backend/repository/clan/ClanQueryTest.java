package com.accsaber.backend.repository.clan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.PersistenceException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoan;
import com.accsaber.backend.model.entity.clan.war.ClanWarLoanStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarRewardItem;
import com.accsaber.backend.model.entity.clan.ClanRival;
import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;
import com.accsaber.backend.model.entity.clan.ClanEquippedItem;
import com.accsaber.backend.model.entity.clan.ClanItem;
import com.accsaber.backend.model.entity.clan.ClanItemSource;
import com.accsaber.backend.model.entity.clan.ClanJoinDirection;
import com.accsaber.backend.model.entity.clan.ClanJoinRequest;
import com.accsaber.backend.model.entity.clan.ClanJoinStatus;
import com.accsaber.backend.model.entity.clan.ClanLevelItem;
import com.accsaber.backend.model.entity.clan.ClanLeaveReason;
import com.accsaber.backend.model.entity.clan.ClanMember;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.clan.ClanSeason;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.UserMission;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.repository.clan.war.ClanWarHitRepository;
import com.accsaber.backend.repository.clan.war.ClanWarLoanRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRewardItemRepository;
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;
import com.accsaber.backend.repository.clan.war.ClanWarPoolEntryRepository;
import com.accsaber.backend.repository.clan.war.ClanWarRepository;
import com.accsaber.backend.model.entity.map.MapDifficultyComplexity;
import com.accsaber.backend.repository.mission.UserMissionRepository;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClanQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ClanRepository clanRepository;
    @Autowired
    private ClanMemberRepository memberRepository;
    @Autowired
    private ClanJoinRequestRepository joinRequestRepository;
    @Autowired
    private ClanXpGrantRepository grantRepository;
    @Autowired
    private ClanItemRepository clanItemRepository;
    @Autowired
    private ClanEquippedItemRepository equippedRepository;
    @Autowired
    private ClanSeasonRepository seasonRepository;
    @Autowired
    private ClanSeasonStandingRepository standingRepository;
    @Autowired
    private UserMissionRepository userMissionRepository;
    @Autowired
    private ClanStandingEventRepository standingEventRepository;
    @Autowired
    private ClanAllianceRepository allianceRepository;
    @Autowired
    private ClanRivalRepository rivalRepository;
    @Autowired
    private ChatMessageRepository chatRepository;
    @Autowired
    private ClanWarRepository warRepository;
    @Autowired
    private ClanWarPoolEntryRepository poolRepository;
    @Autowired
    private ClanWarHitRepository hitRepository;
    @Autowired
    private ClanWarLoanRepository loanRepository;
    @Autowired
    private ClanWarRewardItemRepository rewardItemRepository;
    @Autowired
    private ClanWarParticipantRepository participantRepository;

    private Clan owls;
    private Clan lapiz;
    private User founder;
    private User commander;
    private User officer;
    private User member;

    @BeforeEach
    void seed() {
        founder = user(76561190000000301L, "Founder");
        commander = user(76561190000000302L, "Commander");
        officer = user(76561190000000303L, "Officer");
        member = user(76561190000000304L, "Member");
        owls = clan("Night Owls", "NOW", "night-owls");
        lapiz = clan("El Lápiz", "LPZ", "el-lapiz");
        Instant base = Instant.now().minus(30, ChronoUnit.DAYS);
        seat(owls, member, ClanRole.member, base);
        seat(owls, officer, ClanRole.officer, base.plus(1, ChronoUnit.DAYS));
        seat(owls, commander, ClanRole.commander, base.plus(2, ChronoUnit.DAYS));
        seat(owls, founder, ClanRole.founder, base.plus(3, ChronoUnit.DAYS));
        entityManager.flush();
    }

    private User user(Long id, String name) {
        User user = User.builder().id(id).name(name).country("ES").build();
        entityManager.persist(user);
        return user;
    }

    private Clan clan(String name, String tag, String slug) {
        Clan clan = Clan.builder().name(name).tag(tag).slug(slug).build();
        entityManager.persist(clan);
        return clan;
    }

    private void seat(Clan clan, User user, ClanRole role, Instant joinedAt) {
        entityManager.persist(ClanMember.builder().clan(clan).user(user).role(role).build());
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE clan_members SET joined_at = ?1 WHERE user_id = ?2")
                .setParameter(1, joinedAt).setParameter(2, user.getId()).executeUpdate();
    }

    @Test
    @DisplayName("clan search ignores accents and case, and matches tags too")
    void searchIsNormalised() {
        assertThat(clanRepository.search("lapiz", Pageable.unpaged()).getContent()).containsExactly(lapiz);
        assertThat(clanRepository.search("NIGHT", Pageable.unpaged()).getContent()).containsExactly(owls);
        assertThat(clanRepository.search("lpz", Pageable.unpaged()).getContent()).containsExactly(lapiz);
        assertThat(clanRepository.search(null, Pageable.unpaged()).getContent()).hasSize(2);
    }

    @Test
    @DisplayName("the members sort expression orders clans by open headcount")
    void membersSortExpressionWorks() {
        Pageable byMembers = PageRequest.of(0, 10, JpaSort.unsafe(Sort.Direction.DESC,
                "(SELECT COUNT(m) FROM ClanMember m WHERE m.clan = c AND m.leftAt IS NULL)"));

        assertThat(clanRepository.search(null, byMembers).getContent()).containsExactly(owls, lapiz);
    }

    @Test
    @DisplayName("the roster lists founder, commanders, officers, then members")
    void rosterOrdersByRank() {
        List<ClanRole> roles = memberRepository.findRoster(owls.getId(), Pageable.unpaged()).getContent().stream()
                .map(ClanMember::getRole).toList();

        assertThat(roles).containsExactly(ClanRole.founder, ClanRole.commander, ClanRole.officer, ClanRole.member);
    }

    @Test
    @DisplayName("headcounts and founders load for a whole page in one query each")
    void countsAndFoundersBatch() {
        Map<UUID, Long> counts = memberRepository.countOpenByClanIds(List.of(owls.getId(), lapiz.getId())).stream()
                .collect(Collectors.toMap(ClanMemberRepository.MemberCountView::getClanId,
                        ClanMemberRepository.MemberCountView::getMembers));
        List<ClanMember> founders = memberRepository.findOpenFounders(List.of(owls.getId(), lapiz.getId()));

        assertThat(counts).containsEntry(owls.getId(), 4L).doesNotContainKey(lapiz.getId());
        assertThat(founders).extracting(m -> m.getUser().getName()).containsExactly("Founder");
    }

    @Test
    @DisplayName("disbanding closes every open stint and expires the clan's pending requests")
    void bulkCloseAndExpire() {
        User outsider = user(76561190000000305L, "Outsider");
        joinRequestRepository.saveAndFlush(ClanJoinRequest.builder().clan(owls).user(outsider)
                .direction(ClanJoinDirection.request).createdBy(outsider).build());

        int closed = memberRepository.closeOpenByClanId(owls.getId(), ClanLeaveReason.disbanded, Instant.now());
        int expired = joinRequestRepository.expirePendingForClan(owls.getId(), Instant.now());
        entityManager.clear();

        assertThat(closed).isEqualTo(4);
        assertThat(expired).isEqualTo(1);
        assertThat(memberRepository.countByClan_IdAndLeftAtIsNull(owls.getId())).isZero();
        assertThat(memberRepository.findFirstByUser_IdOrderByJoinedAtDesc(founder.getId()))
                .get().extracting(ClanMember::getLeaveReason).isEqualTo(ClanLeaveReason.disbanded);
        assertThat(joinRequestRepository.existsByClan_IdAndUser_IdAndStatus(owls.getId(), outsider.getId(),
                ClanJoinStatus.expired)).isTrue();
    }

    @Test
    @DisplayName("an open membership resolves with its clan for the player")
    void openMembershipByPlayer() {
        assertThat(memberRepository.findOpenByUserId(officer.getId()))
                .get().extracting(m -> m.getClan().getName()).isEqualTo("Night Owls");
        assertThat(clanRepository.findByIdAndActiveTrueForUpdate(owls.getId())).contains(owls);
    }

    private MapDifficulty rankedDifficulty() {
        Category trueAcc = entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'true_acc' AND c.active = true", Category.class)
                .getSingleResult();
        com.accsaber.backend.model.entity.map.Map map = com.accsaber.backend.model.entity.map.Map.builder()
                .songName("Song").songAuthor("Author").songHash("hash-" + UUID.randomUUID()).mapAuthor("Mapper")
                .build();
        entityManager.persist(map);
        MapDifficulty difficulty = MapDifficulty.builder().map(map).category(trueAcc)
                .difficulty(Difficulty.EXPERT_PLUS).characteristic("Standard")
                .status(MapDifficultyStatus.RANKED).maxScore(1000000).build();
        entityManager.persist(difficulty);
        return difficulty;
    }

    private void score(User player, MapDifficulty difficulty, double xp, Instant timeSet) {
        entityManager.persist(Score.builder().user(player).mapDifficulty(difficulty).score(950000)
                .scoreNoMods(950000).rank(1).rankWhenSet(1).ap(400.0).weightedAp(400.0).xpGained(xp)
                .active(false).supersedesReason("Worse score").timeSet(timeSet).build());
    }

    private Item clanItem(String typeKey, String name) {
        ItemType type = entityManager.createQuery("SELECT t FROM ItemType t WHERE t.key = :key", ItemType.class)
                .setParameter("key", typeKey).getSingleResult();
        Item item = Item.builder().type(type).name(name).serialized(false).build();
        entityManager.persist(item);
        return item;
    }

    @Test
    @DisplayName("daily play XP only counts scores a player set while in the clan, on that day, not yet granted")
    void dailyPlayXpRespectsTheMembershipWindow() {
        MapDifficulty difficulty = rankedDifficulty();
        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(2, ChronoUnit.DAYS);
        Instant midday = dayStart.plus(12, ChronoUnit.HOURS);
        score(founder, difficulty, 100.0, midday);
        score(founder, difficulty, 40.0, midday.plus(1, ChronoUnit.HOURS));
        score(founder, difficulty, 50.0, midday.minus(1, ChronoUnit.DAYS));
        score(member, difficulty, 70.0, midday);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE clan_members SET left_at = ?1, leave_reason = 'left' WHERE user_id = ?2")
                .setParameter(1, dayStart).setParameter(2, member.getId()).executeUpdate();
        String day = dayStart.toString().substring(0, 10);

        List<ClanXpGrantRepository.MemberPlayXpView> rows = grantRepository.sumUngrantedMemberPlayXp(dayStart,
                dayStart.plus(1, ChronoUnit.DAYS), day);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getClanId()).isEqualTo(owls.getId());
        assertThat(rows.get(0).getXp()).isEqualTo(140.0);

        grantRepository.insertIfAbsent(owls.getId(), "daily_play", day, 140.0, 1.0, 140.0);

        assertThat(grantRepository.sumUngrantedMemberPlayXp(dayStart, dayStart.plus(1, ChronoUnit.DAYS), day))
                .isEmpty();
    }

    @Test
    @DisplayName("an XP grant inserts once per clan, source and source id")
    void grantInsertIsIdempotent() {
        assertThat(grantRepository.insertIfAbsent(owls.getId(), "mission", "m-1", 50.0, 1.0, 50.0)).isEqualTo(1);
        assertThat(grantRepository.insertIfAbsent(owls.getId(), "mission", "m-1", 50.0, 1.0, 50.0)).isZero();
    }

    @Test
    @DisplayName("levelling grants the cosmetics of every level crossed, once each")
    void levelItemsGrantForTheCrossedRangeOnly() {
        Item levelOne = clanItem("clan_emblem", "Level One Emblem");
        Item levelThree = clanItem("clan_banner", "Level Three Banner");
        entityManager.persist(ClanLevelItem.builder().item(levelOne).level(1).build());
        entityManager.persist(ClanLevelItem.builder().item(levelThree).level(3).build());
        entityManager.flush();

        assertThat(clanItemRepository.grantLevelItems(owls.getId(), 0, 2)).isEqualTo(1);
        assertThat(clanItemRepository.grantLevelItems(owls.getId(), 0, 2)).isZero();
        assertThat(clanItemRepository.existsByClan_IdAndItem_Id(owls.getId(), levelOne.getId())).isTrue();
        assertThat(clanItemRepository.existsByClan_IdAndItem_Id(owls.getId(), levelThree.getId())).isFalse();
    }

    @Test
    @DisplayName("equipped cosmetics load for many clans in one query")
    void equippedLoadsForManyClans() {
        Item emblem = clanItem("clan_emblem", "Owl Emblem");
        entityManager.persist(ClanItem.builder().clan(owls).item(emblem).source(ClanItemSource.manual).build());
        entityManager.persist(ClanEquippedItem.builder().clan(owls).itemType(emblem.getType()).item(emblem).build());
        entityManager.flush();
        entityManager.clear();

        List<ClanEquippedItem> equipped = equippedRepository.findByClanIds(List.of(owls.getId(), lapiz.getId()));

        assertThat(equipped).extracting(e -> e.getItem().getName()).containsExactly("Owl Emblem");
        assertThat(equippedRepository.deleteSlot(owls.getId(), "clan_emblem")).isEqualTo(1);
    }

    private Category overall() {
        return entityManager
                .createQuery("SELECT c FROM Category c WHERE c.code = 'overall' AND c.active = true", Category.class)
                .getSingleResult();
    }

    private void skill(User player, double level) {
        entityManager.persist(UserCategorySkill.builder().user(player).category(overall()).skillLevel(level)
                .rankScore(0).sustainedScore(0).peakScore(0).combinedScore(0).topAp(0).activePlayers(1L).build());
    }

    private UUID season(Instant startsAt, Instant endsAt) {
        ClanSeason season = ClanSeason.builder().name("S " + startsAt).slug("s-" + startsAt.toEpochMilli())
                .startsAt(startsAt).endsAt(endsAt).build();
        entityManager.persist(season);
        entityManager.flush();
        return season.getId();
    }

    private UUID war(UUID seasonId, Clan attacker, Clan defender) {
        UUID id = (UUID) entityManager.createNativeQuery("""
                INSERT INTO clan_wars (season_id, attacker_clan_id, defender_clan_id, declared_by, arena, arena_spec, ruleset)
                VALUES (?1, ?2, ?3, ?4, 'mixed', CAST('{}' AS jsonb), 'duel') RETURNING id
                """).setParameter(1, seasonId).setParameter(2, attacker.getId()).setParameter(3, defender.getId())
                .setParameter(4, founder.getId()).getSingleResult();
        for (Clan side : List.of(attacker, defender)) {
            entityManager.createNativeQuery("INSERT INTO clan_war_sides (war_id, clan_id, stake, stake_remaining, "
                    + "standing_at_declare) VALUES (?1, ?2, 10, 10, 10)")
                    .setParameter(1, id).setParameter(2, side.getId()).executeUpdate();
        }
        return id;
    }

    private UUID alliance(Clan first, Clan second, String status, Instant acceptedAt) {
        return (UUID) entityManager.createNativeQuery("""
                INSERT INTO clan_alliances (clan_a_id, clan_b_id, proposed_by_clan_id, proposed_by_user_id, status,
                    accepted_at)
                VALUES (LEAST(CAST(?1 AS uuid), CAST(?2 AS uuid)), GREATEST(CAST(?1 AS uuid), CAST(?2 AS uuid)), ?1, ?3,
                    ?4, ?5)
                RETURNING id
                """).setParameter(1, first.getId()).setParameter(2, second.getId()).setParameter(3, founder.getId())
                .setParameter(4, status).setParameter(5, acceptedAt).getSingleResult();
    }

    @Test
    @DisplayName("roster strength reads every open member's overall skill")
    void memberSkillsForStrength() {
        skill(founder, 70.0);
        skill(officer, 40.0);
        entityManager.flush();

        List<ClanMemberRepository.ClanSkillView> skills = memberRepository.findOpenMemberSkills(
                List.of(owls.getId(), lapiz.getId()), overall().getId());

        assertThat(skills).extracting(ClanMemberRepository.ClanSkillView::getSkill).containsExactlyInAnyOrder(70.0, 40.0);
        assertThat(skills).extracting(ClanMemberRepository.ClanSkillView::getClanId).containsOnly(owls.getId());
    }

    @Test
    @DisplayName("an ally only lends its top player's skill once it has fought a war this season")
    void allyStrengthNeedsAnAllyThatFought() {
        User lapizStar = user(76561190000000306L, "Lapiz Star");
        User lapizBench = user(76561190000000307L, "Lapiz Bench");
        seat(lapiz, lapizStar, ClanRole.founder, Instant.now().minus(5, ChronoUnit.DAYS));
        seat(lapiz, lapizBench, ClanRole.member, Instant.now().minus(5, ChronoUnit.DAYS));
        skill(lapizStar, 90.0);
        skill(lapizBench, 50.0);
        alliance(owls, lapiz, "active", Instant.now());

        assertThat(memberRepository.findFoughtAllyTopSkills(List.of(owls.getId()), overall().getId(), Instant.now()))
                .isEmpty();

        Clan rival = clan("Rivals", "RIV", "rivals");
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        war(current, lapiz, rival);

        assertThat(memberRepository.findFoughtAllyTopSkills(List.of(owls.getId()), overall().getId(), Instant.now()))
                .extracting(ClanMemberRepository.ClanSkillView::getSkill).containsExactly(90.0);
    }

    @Test
    @DisplayName("the live ranking orders by base plus earned and a single rank agrees with it")
    void liveRankingAndRankAgree() {
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        entityManager.createNativeQuery("UPDATE clans SET roster_strength = 10 WHERE id = ?1")
                .setParameter(1, owls.getId()).executeUpdate();
        entityManager.createNativeQuery("UPDATE clans SET roster_strength = 5 WHERE id = ?1")
                .setParameter(1, lapiz.getId()).executeUpdate();
        entityManager.createNativeQuery("INSERT INTO clan_season_standings (season_id, clan_id, earned) VALUES (?1, ?2, 80)")
                .setParameter(1, current).setParameter(2, lapiz.getId()).executeUpdate();

        List<ClanSeasonStandingRepository.RankingRow> ranking = standingRepository.findFullLiveRanking(current, 10.0);

        assertThat(ranking).extracting(ClanSeasonStandingRepository.RankingRow::getClanId)
                .containsExactly(lapiz.getId(), owls.getId());
        assertThat(ranking.get(0).getBaseStanding()).isEqualTo(50.0);
        assertThat(ranking.get(0).getEarned()).isEqualTo(80.0);
        entityManager.clear();
        Clan freshOwls = clanRepository.findById(owls.getId()).orElseThrow();
        assertThat(standingRepository.findLiveRank(current, 10.0, 100.0, freshOwls.getCreatedAt(), owls.getId()))
                .isEqualTo(2);
        assertThat(standingRepository.findLiveRanking(current, 10.0, PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("season lookups find the running season, the ended ones and whether one is still open")
    void seasonLookups() {
        Instant now = Instant.now();
        UUID ended = season(now.minus(60, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS));
        UUID running = season(now.minus(10, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));

        assertThat(seasonRepository.findCurrent(now)).get().extracting(ClanSeason::getId).isEqualTo(running);
        assertThat(seasonRepository.findEndedUnclosedIds(now)).containsExactly(ended);
        assertThat(seasonRepository.existsOpenUntilAfter(now)).isTrue();
        assertThat(seasonRepository.findTopByOrderByEndsAtDesc()).get().extracting(ClanSeason::getId)
                .isEqualTo(running);
    }

    @Test
    @DisplayName("season contributors add war contribution to their share of completed clan missions, biggest first")
    void seasonContributorsOrderByContribution() {
        Clan rival = clan("Rivals", "RIV", "rivals");
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID firstWar = war(current, owls, rival);
        UUID secondWar = war(current, rival, owls);
        String participant = "INSERT INTO clan_war_participants (war_id, user_id, clan_id, lent_by_clan_id, "
                + "standing_weight, guard, contribution) VALUES (?1, ?2, ?3, ?4, 0.5, 100, ?5)";
        entityManager.createNativeQuery(participant).setParameter(1, firstWar).setParameter(2, officer.getId())
                .setParameter(3, owls.getId()).setParameter(4, null).setParameter(5, 30.0).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, secondWar).setParameter(2, officer.getId())
                .setParameter(3, owls.getId()).setParameter(4, null).setParameter(5, 40.0).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, firstWar).setParameter(2, founder.getId())
                .setParameter(3, owls.getId()).setParameter(4, null).setParameter(5, 50.0).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, firstWar).setParameter(2, member.getId())
                .setParameter(3, owls.getId()).setParameter(4, null).setParameter(5, 0.0).executeUpdate();

        UUID template = template("clan-counter", "clan", "{\"count\": 4}");
        UUID done = mission(template, owls, null, null, "completed", 4, Instant.now().minus(1, ChronoUnit.DAYS));
        contribution(done, member, 1.0);
        contribution(done, founder, 3.0);

        List<ClanSeasonRepository.ContributorView> contributors = seasonRepository
                .findContributors(current, owls.getId(), 100.0);

        assertThat(contributors).extracting(ClanSeasonRepository.ContributorView::getUserId)
                .containsExactly(founder.getId(), officer.getId(), member.getId());
        assertThat(contributors.get(0).getContribution()).isEqualTo(125.0);
        assertThat(contributors.get(1).getContribution()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("alliance lookups see both sides of the ordered pair and only open rows")
    void allianceLookups() {
        Clan rival = clan("Rivals", "RIV", "rivals");
        Clan former = clan("Former", "FRM", "former");
        alliance(owls, lapiz, "active", Instant.now());
        alliance(rival, owls, "pending", null);
        alliance(owls, former, "ended", Instant.now().minus(9, ChronoUnit.DAYS));
        UUID low = (UUID) entityManager.createNativeQuery("SELECT LEAST(CAST(?1 AS uuid), CAST(?2 AS uuid))")
                .setParameter(1, owls.getId()).setParameter(2, lapiz.getId()).getSingleResult();
        UUID high = low.equals(owls.getId()) ? lapiz.getId() : owls.getId();
        entityManager.clear();

        assertThat(allianceRepository.findPageByClanIdAndStatus(owls.getId(), ClanAllianceStatus.active,
                Pageable.unpaged()).getContent()).singleElement()
                .satisfies(a -> assertThat(a.otherThan(owls.getId()).getName()).isEqualTo("El Lápiz"));
        assertThat(allianceRepository.findPageByClanIdAndStatus(lapiz.getId(), ClanAllianceStatus.active,
                PageRequest.of(0, 5)).getTotalElements()).isEqualTo(1);
        assertThat(allianceRepository.findOpenByClanId(owls.getId())).hasSize(2);
        assertThat(allianceRepository.existsOpenBetween(low, high)).isTrue();
        assertThat(allianceRepository.countActiveByClanId(owls.getId())).isEqualTo(1);
        assertThat(allianceRepository.findActiveAllyIds(List.of(owls.getId()))).containsExactly(lapiz.getId());
        assertThat(allianceRepository.findActiveAllyIds(List.of(lapiz.getId()))).containsExactly(owls.getId());
    }

    @Test
    @DisplayName("trust sums what lent players put up across the alliance since it formed, in both directions")
    void trustContributionsCountLoansSinceTheAllianceFormed() {
        Clan rival = clan("Rivals", "RIV", "rivals");
        User lapizLender = user(76561190000000308L, "Lapiz Lender");
        seat(lapiz, lapizLender, ClanRole.member, Instant.now().minus(60, ChronoUnit.DAYS));
        UUID allianceId = alliance(owls, lapiz, "active", Instant.now().minus(20, ChronoUnit.DAYS));
        UUID current = season(Instant.now().minus(40, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID lapizWar = war(current, lapiz, rival);
        UUID owlsWar = war(current, owls, rival);
        UUID olderWar = war(current, rival, lapiz);
        loan(lapizWar, lapiz, owls, officer, "ended", Instant.now().minus(5, ChronoUnit.DAYS), 120.0);
        loan(owlsWar, owls, lapiz, lapizLender, "accepted", Instant.now().minus(2, ChronoUnit.DAYS), 30.0);
        loan(olderWar, lapiz, owls, member, "ended", Instant.now().minus(30, ChronoUnit.DAYS), 500.0);
        loan(lapizWar, lapiz, owls, commander, "declined", Instant.now().minus(4, ChronoUnit.DAYS), 700.0);

        assertThat(allianceRepository.findTrustContributions(List.of(allianceId)))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.getAllianceId()).isEqualTo(allianceId);
                    assertThat(view.getContribution()).isEqualTo(150.0);
                });
    }

    private void loan(UUID warId, Clan borrower, Clan lender, User player, String status, Instant createdAt,
            double contribution) {
        entityManager.createNativeQuery("""
                INSERT INTO clan_war_loans (war_id, clan_id, lending_clan_id, user_id, offered_by, status, created_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                """).setParameter(1, warId).setParameter(2, borrower.getId()).setParameter(3, lender.getId())
                .setParameter(4, player.getId()).setParameter(5, founder.getId()).setParameter(6, status)
                .setParameter(7, createdAt).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO clan_war_participants (war_id, user_id, clan_id, lent_by_clan_id, standing_weight, guard,
                    contribution)
                VALUES (?1, ?2, ?3, ?4, 0.5, 100, ?5)
                """).setParameter(1, warId).setParameter(2, player.getId()).setParameter(3, borrower.getId())
                .setParameter(4, lender.getId()).setParameter(5, contribution).executeUpdate();
    }

    private UUID template(String code, String pool, String targets) {
        return (UUID) entityManager.createNativeQuery("""
                INSERT INTO mission_templates (code, name, description, type, pool, event_targets, completable_until)
                VALUES (?1, ?1, 'Clan work', 'SCORES_N', ?2, CAST(?3 AS jsonb), NOW() + INTERVAL '7 days')
                RETURNING id
                """).setParameter(1, code).setParameter(2, pool).setParameter(3, targets).getSingleResult();
    }

    private UUID mission(UUID templateId, Clan clan, User user, UUID parentId, String status, int progress,
            Instant completedAt) {
        return (UUID) entityManager.createNativeQuery("""
                INSERT INTO user_missions (template_id, pool, clan_id, user_id, parent_mission_id, status,
                    progress_count, target_count, completed_at, expires_at)
                VALUES (?1, ?8, ?2, ?3, ?4, ?5, ?6, 4, ?7, NOW() + INTERVAL '3 days')
                RETURNING id
                """).setParameter(1, templateId).setParameter(8, clan != null ? "clan" : "community")
                .setParameter(2, clan != null ? clan.getId() : null)
                .setParameter(3, user != null ? user.getId() : null).setParameter(4, parentId)
                .setParameter(5, status).setParameter(6, progress).setParameter(7, completedAt).getSingleResult();
    }

    private void contribution(UUID missionId, User user, double amount) {
        entityManager.createNativeQuery("INSERT INTO mission_contributions (user_mission_id, user_id, contribution) "
                + "VALUES (?1, ?2, ?3)").setParameter(1, missionId).setParameter(2, user.getId())
                .setParameter(3, amount).executeUpdate();
    }

    private MissionStatus statusOf(UUID missionId) {
        entityManager.clear();
        return userMissionRepository.findById(missionId).map(UserMission::getStatus).orElseThrow();
    }

    @Test
    @DisplayName("clan mission lookups keep counters, per member parents and member rows apart")
    void clanMissionLookups() {
        User outsider = user(76561190000000309L, "Outsider");
        UUID community = mission(template("community", "community", "{\"count\": 50}"), null, null, null,
                "active", 0, null);
        UUID counter = mission(template("counter", "clan", "{\"count\": 20}"), owls, null, null, "active", 0, null);
        UUID perMember = template("per-member", "clan", null);
        UUID parent = mission(perMember, owls, null, null, "active", 0, null);
        UUID founderRow = mission(perMember, owls, founder, parent, "active", 0, null);
        entityManager.flush();

        assertThat(userMissionRepository.findActiveSharedFor(founder.getId())).extracting(UserMission::getId)
                .containsExactlyInAnyOrder(community, counter);
        assertThat(userMissionRepository.findActiveSharedFor(outsider.getId())).extracting(UserMission::getId)
                .containsExactly(community);
        assertThat(userMissionRepository.findClanShared(owls.getId(), true, Instant.now(), PageRequest.of(0, 10))
                .getContent()).extracting(UserMission::getId).containsExactlyInAnyOrder(counter, parent);
        assertThat(userMissionRepository.findPerMemberParentsMissing(owls.getId(), officer.getId(), Instant.now()))
                .extracting(UserMission::getId).containsExactly(parent);
        assertThat(userMissionRepository.findPerMemberParentsMissing(owls.getId(), founder.getId(), Instant.now()))
                .isEmpty();
        assertThat(userMissionRepository.findClanIdsWithoutCurrentMissions(Instant.now()))
                .contains(lapiz.getId()).doesNotContain(owls.getId());
        assertThat(userMissionRepository.findSharedById(parent)).isPresent();
        assertThat(userMissionRepository.findSharedById(founderRow)).isEmpty();

        assertThat(userMissionRepository.voidActiveClanRowsForUser(founder.getId())).isEqualTo(1);
        assertThat(statusOf(founderRow)).isEqualTo(MissionStatus.voided);
        assertThat(userMissionRepository.expireActiveForClan(owls.getId())).isEqualTo(2);
        assertThat(statusOf(counter)).isEqualTo(MissionStatus.expired);
        assertThat(statusOf(community)).isEqualTo(MissionStatus.active);
    }

    @Test
    @DisplayName("a clan mission past its week expires and a live one does not")
    void staleClanMissionsExpire() {
        UUID counter = mission(template("stale", "clan", "{\"count\": 20}"), owls, null, null, "active", 0, null);
        UUID live = mission(template("live", "clan", "{\"count\": 20}"), lapiz, null, null, "active", 0, null);
        entityManager.createNativeQuery("UPDATE user_missions SET expires_at = NOW() - INTERVAL '1 minute' WHERE id = ?1")
                .setParameter(1, counter).executeUpdate();

        assertThat(userMissionRepository.expireStaleClanMissions(Instant.now())).isEqualTo(1);
        assertThat(statusOf(counter)).isEqualTo(MissionStatus.expired);
        assertThat(statusOf(live)).isEqualTo(MissionStatus.active);
    }

    @Test
    @DisplayName("the Standing writer's queries create the row once, lock it and bank each source once")
    void standingWriterQueries() {
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(standingRepository.ensureRow(current, owls.getId())).isEqualTo(1);
        assertThat(standingRepository.ensureRow(current, owls.getId())).isZero();
        assertThat(standingRepository.lockEarned(current, owls.getId())).isZero();
        assertThat(standingEventRepository.insertIfAbsent(current, owls.getId(), 40.0, "mission", "m-1")).isEqualTo(1);
        assertThat(standingEventRepository.insertIfAbsent(current, owls.getId(), 40.0, "mission", "m-1")).isZero();
        assertThat(standingRepository.addEarned(current, owls.getId(), 40.0)).isEqualTo(1);
        assertThat(standingRepository.lockEarned(current, owls.getId())).isEqualTo(40.0);
    }

    @Test
    @DisplayName("rival lookups read both directions, skip dropped rows and disbanded clans, and see either order")
    void rivalLookups() {
        Clan rival = clan("Rivals", "RIV", "rivals");
        Clan gone = clan("Gone", "GONE", "gone");
        entityManager.persist(ClanRival.builder().clan(owls).rivalClan(lapiz).declaredBy(founder).build());
        entityManager.persist(ClanRival.builder().clan(rival).rivalClan(owls).build());
        entityManager.persist(ClanRival.builder().clan(owls).rivalClan(gone).build());
        entityManager.persist(ClanRival.builder().clan(lapiz).rivalClan(rival).active(false).build());
        gone.setActive(false);
        entityManager.flush();
        entityManager.clear();

        assertThat(rivalRepository.findDeclaredBy(owls.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(r -> r.getRivalClan().getName()).containsExactly("El Lápiz");
        assertThat(rivalRepository.findDeclaredAgainst(owls.getId(), PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(1);
        assertThat(rivalRepository.existsActiveBetween(lapiz.getId(), owls.getId())).isTrue();
        assertThat(rivalRepository.existsActiveBetween(lapiz.getId(), rival.getId())).isFalse();
        assertThat(rivalRepository.findByClan_IdAndRivalClan_Id(lapiz.getId(), rival.getId())).isPresent();
    }

    @Test
    @DisplayName("an active alliance is found whichever clan is asked about first")
    void activeAllianceEitherWay() {
        alliance(owls, lapiz, "active", Instant.now());
        Clan rival = clan("Rivals", "RIV", "rivals");
        alliance(owls, rival, "pending", null);

        assertThat(allianceRepository.existsActiveBetween(owls.getId(), lapiz.getId())).isTrue();
        assertThat(allianceRepository.existsActiveBetween(lapiz.getId(), owls.getId())).isTrue();
        assertThat(allianceRepository.existsActiveBetween(owls.getId(), rival.getId())).isFalse();
    }

    @Test
    @DisplayName("clan chat pages newest first with its subjects, one clan at a time")
    void clanChatPages() {
        for (int i = 0; i < 3; i++) {
            entityManager.persist(ChatMessage.builder().clan(owls).user(member).content("msg " + i).build());
        }
        entityManager.persist(ChatMessage.builder().clan(owls).user(founder).event(ChatEvent.alliance_formed)
                .subjectClan(lapiz).build());
        entityManager.persist(ChatMessage.builder().clan(lapiz).user(founder).content("elsewhere").build());
        entityManager.flush();

        entityManager.clear();

        List<ChatMessage> page = chatRepository.findByClan_IdOrderByCreatedAtDesc(owls.getId(), PageRequest.of(0, 10))
                .getContent();
        assertThat(page).hasSize(4);
        assertThat(page).filteredOn(m -> m.getEvent() == ChatEvent.alliance_formed).singleElement()
                .satisfies(m -> assertThat(m.getSubjectClan().getTag()).isEqualTo("LPZ"));

        assertThat(chatRepository.findByClan_IdOrderByCreatedAtDesc(lapiz.getId(), PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    private void warState(UUID warId, String sql, Object value) {
        entityManager.createNativeQuery("UPDATE clan_wars SET " + sql + " WHERE id = ?2")
                .setParameter(1, value).setParameter(2, warId).executeUpdate();
    }

    @Test
    @DisplayName("war lookups find open attacks, due pick windows and starts, quiet wars and a season's open wars")
    void warLookups() {
        Clan rival = clan("Rivals", "RIV", "rivals");
        Instant now = Instant.now();
        UUID current = season(now.minus(20, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
        UUID picking = war(current, owls, lapiz);
        UUID preparing = war(current, lapiz, rival);
        UUID quiet = war(current, rival, owls);
        warState(picking, "picks_due_at = ?1", now.minusSeconds(60));
        warState(preparing, "status = 'preparing', starts_at = ?1", now.minusSeconds(60));
        warState(quiet, "status = 'active', starts_at = ?1", now.minus(10, ChronoUnit.DAYS));

        assertThat(warRepository.existsOpenAttack(owls.getId(), null)).isTrue();
        assertThat(warRepository.existsOpenAttack(owls.getId(), lapiz.getId())).isTrue();
        assertThat(warRepository.existsOpenAttack(lapiz.getId(), owls.getId())).isFalse();
        assertThat(warRepository.findPicksDue(now)).containsExactly(picking);
        assertThat(warRepository.findStartsDue(now)).containsExactly(preparing);
        assertThat(warRepository.findQuietSince(now.minus(7, ChronoUnit.DAYS))).containsExactly(quiet);
        assertThat(warRepository.findQuietSince(now.minus(11, ChronoUnit.DAYS))).isEmpty();
        assertThat(warRepository.findOpenIdsBySeasonId(current)).containsExactlyInAnyOrder(picking, preparing, quiet);
        assertThat(warRepository.findOpenIdsByClanId(owls.getId())).containsExactlyInAnyOrder(picking, quiet);
        assertThat(warRepository.findActiveIdsByClanId(owls.getId())).containsExactly(quiet);
        assertThat(warRepository.findPage(owls.getId(), true, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);

        warState(quiet, "status = 'ended', outcome = 'drawn', ended_at = ?1", now);
        assertThat(warRepository.findPage(owls.getId(), true, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(warRepository.findPage(null, false, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
        assertThat(warRepository.findWithRefsById(picking)).get()
                .satisfies(war -> assertThat(war.getAttackerClan().getTag()).isEqualTo("NOW"));
    }

    private MapDifficulty rankedAt(double complexity) {
        MapDifficulty difficulty = rankedDifficulty();
        entityManager.persist(MapDifficultyComplexity.builder().mapDifficulty(difficulty).complexity(complexity).build());
        entityManager.flush();
        return difficulty;
    }

    @Test
    @DisplayName("the arena decides which ranked maps are legal and random fills skip what the pool already has")
    void poolLegalityAndRandomFill() {
        MapDifficulty easy = rankedAt(4.0);
        MapDifficulty mid = rankedAt(8.0);
        MapDifficulty hard = rankedAt(12.0);
        UUID trueAcc = easy.getCategory().getId();
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID warId = war(current, owls, lapiz);
        entityManager.createNativeQuery("INSERT INTO clan_war_pool (war_id, map_difficulty_id, picked_by_clan_id, "
                + "source) VALUES (?1, ?2, ?3, 'pick')").setParameter(1, warId).setParameter(2, mid.getId())
                .setParameter(3, owls.getId()).executeUpdate();

        assertThat(poolRepository.findLegal(List.of(easy.getId(), mid.getId(), hard.getId()), trueAcc, 5.0, 13.0))
                .containsExactlyInAnyOrder(mid.getId(), hard.getId());
        assertThat(poolRepository.findLegal(List.of(easy.getId()), overall().getId(), null, null)).isEmpty();

        assertThat(poolRepository.insertRandom(warId, lapiz.getId(), "replacement", 5, trueAcc, 5.0, 13.0))
                .isEqualTo(1);
        entityManager.clear();

        assertThat(poolRepository.findByWarId(warId)).extracting(entry -> entry.getMapDifficulty().getId())
                .containsExactlyInAnyOrder(mid.getId(), hard.getId());
        assertThat(poolRepository.findDifficulties(warId)).extracting(d -> d.getMap().getSongName())
                .containsOnly("Song");
    }

    @Test
    @DisplayName("member skills for a war roster include members with no overall skill yet at zero")
    void rosterSkillsIncludeUnrankedMembers() {
        skill(founder, 70.0);
        entityManager.flush();

        List<ClanMemberRepository.MemberSkillView> skills = memberRepository.findOpenMemberSkillsByClan(owls.getId(),
                overall().getId());

        assertThat(skills).hasSize(4);
        assertThat(skills).filteredOn(view -> view.getUserId().equals(founder.getId())).singleElement()
                .satisfies(view -> assertThat(view.getSkill()).isEqualTo(70.0));
        assertThat(skills).filteredOn(view -> !view.getUserId().equals(founder.getId()))
                .allSatisfy(view -> assertThat(view.getSkill()).isZero());
    }

    @Test
    @DisplayName("combat lookups find the wars a play can land in, the gate's sets, skills and enemy scores")
    void combatLookups() {
        MapDifficulty pooled = rankedAt(6.0);
        MapDifficulty elsewhere = rankedAt(6.0);
        skill(founder, 55.0);
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID warId = war(current, owls, lapiz);
        User lapizStar = user(76561190000000310L, "Lapiz Star");
        seat(lapiz, lapizStar, ClanRole.founder, Instant.now().minus(5, ChronoUnit.DAYS));
        warState(warId, "status = 'active', starts_at = ?1", Instant.now().minusSeconds(60));
        entityManager.createNativeQuery("INSERT INTO clan_war_pool (war_id, map_difficulty_id, source) VALUES "
                + "(?1, ?2, 'random')").setParameter(1, warId).setParameter(2, pooled.getId()).executeUpdate();
        String participant = "INSERT INTO clan_war_participants (war_id, user_id, clan_id, standing_weight, guard, "
                + "left_at) VALUES (?1, ?2, ?3, 0.5, 100, ?4)";
        entityManager.createNativeQuery(participant).setParameter(1, warId).setParameter(2, founder.getId())
                .setParameter(3, owls.getId()).setParameter(4, null).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, warId).setParameter(2, member.getId())
                .setParameter(3, owls.getId()).setParameter(4, Instant.now()).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, warId).setParameter(2, lapizStar.getId())
                .setParameter(3, lapiz.getId()).setParameter(4, null).executeUpdate();
        score(lapizStar, pooled, 0.0, Instant.now());
        entityManager.createNativeQuery("UPDATE scores SET active = true WHERE user_id = ?1")
                .setParameter(1, lapizStar.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThat(warRepository.findActiveIdsFighting(founder.getId(), pooled.getId())).containsExactly(warId);
        assertThat(warRepository.findActiveIdsFighting(founder.getId(), elsewhere.getId())).isEmpty();
        assertThat(warRepository.findActiveIdsFighting(member.getId(), pooled.getId())).isEmpty();
        assertThat(warRepository.findActivePoolDifficultyIds()).containsExactly(pooled.getId());
        assertThat(warRepository.findActiveParticipantIds()).containsExactlyInAnyOrder(founder.getId(), lapizStar.getId());
        assertThat(participantRepository.findActiveByWarId(warId)).hasSize(2);
        assertThat(participantRepository.findOverallSkills(List.of(founder.getId(), lapizStar.getId())))
                .extracting(ClanMemberRepository.MemberSkillView::getUserId).containsExactly(founder.getId());
        assertThat(hitRepository.findActiveScores(pooled.getId(), List.of(lapizStar.getId(), founder.getId())))
                .singleElement().satisfies(view -> {
                    assertThat(view.getUserId()).isEqualTo(lapizStar.getId());
                    assertThat(view.getScore()).isEqualTo(950000);
                });
        assertThat(hitRepository.countByWar_IdAndVictim_IdAndVictimCycle(warId, lapizStar.getId(), 0)).isZero();
        assertThat(hitRepository.findPageByWarId(warId, PageRequest.of(0, 10)).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("a guard breaks once per cycle, whatever map the second break comes from")
    void oneBreakPerGuardCycle() {
        MapDifficulty first = rankedAt(6.0);
        MapDifficulty second = rankedAt(7.0);
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID warId = war(current, owls, lapiz);
        User lapizStar = user(76561190000000311L, "Lapiz Star");
        String participant = "INSERT INTO clan_war_participants (war_id, user_id, clan_id, standing_weight, guard) "
                + "VALUES (?1, ?2, ?3, 1, 100)";
        entityManager.createNativeQuery(participant).setParameter(1, warId).setParameter(2, founder.getId())
                .setParameter(3, owls.getId()).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, warId).setParameter(2, lapizStar.getId())
                .setParameter(3, lapiz.getId()).executeUpdate();
        score(founder, first, 0.0, Instant.now());
        entityManager.flush();
        UUID scoreId = (UUID) entityManager.createNativeQuery("SELECT id FROM scores WHERE user_id = ?1")
                .setParameter(1, founder.getId()).getSingleResult();
        String hit = "INSERT INTO clan_war_hits (war_id, attacker_user_id, victim_user_id, victim_cycle, "
                + "map_difficulty_id, attacker_score_id, damage, guard_after, broke) VALUES (?1, ?2, ?3, 0, ?4, ?5, "
                + "100, 0, true)";
        entityManager.createNativeQuery(hit).setParameter(1, warId).setParameter(2, founder.getId())
                .setParameter(3, lapizStar.getId()).setParameter(4, first.getId()).setParameter(5, scoreId)
                .executeUpdate();

        assertThatThrownBy(() -> entityManager.createNativeQuery(hit).setParameter(1, warId)
                .setParameter(2, founder.getId()).setParameter(3, lapizStar.getId()).setParameter(4, second.getId())
                .setParameter(5, scoreId).executeUpdate()).isInstanceOf(PersistenceException.class);
    }

    private UUID loan(UUID warId, Clan into, Clan lender, User player, String status, Instant endedAt) {
        return (UUID) entityManager.createNativeQuery("""
                INSERT INTO clan_war_loans (war_id, clan_id, lending_clan_id, user_id, offered_by, status, ended_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7) RETURNING id
                """).setParameter(1, warId).setParameter(2, into.getId()).setParameter(3, lender.getId())
                .setParameter(4, player.getId()).setParameter(5, founder.getId()).setParameter(6, status)
                .setParameter(7, endedAt).getSingleResult();
    }

    @Test
    @DisplayName("loan lookups count open loans by lender, war and pair, and a war's end closes them")
    void loanLookups() {
        Clan rival = clan("Rivals", "RIV", "rivals");
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID warId = war(current, lapiz, rival);
        Instant ended = Instant.now().minus(2, ChronoUnit.DAYS);
        UUID accepted = loan(warId, lapiz, owls, officer, "accepted", null);
        UUID pending = loan(warId, rival, owls, member, "pending", null);
        loan(warId, lapiz, owls, commander, "ended", ended);
        alliance(owls, lapiz, "active", Instant.now());
        entityManager.flush();

        assertThat(loanRepository.existsOpenByUserId(officer.getId())).isTrue();
        assertThat(loanRepository.existsOpenByUserId(commander.getId())).isFalse();
        assertThat(loanRepository.findLastEndedAt(commander.getId())).get()
                .satisfies(at -> assertThat(at).isCloseTo(ended, within(1, ChronoUnit.MILLIS)));
        assertThat(loanRepository.countOpenByLendingClanId(owls.getId())).isEqualTo(2);
        assertThat(loanRepository.countOpenIntoWar(warId, lapiz.getId())).isEqualTo(1);
        assertThat(loanRepository.countOpenBetween(owls.getId(), rival.getId())).isEqualTo(1);
        assertThat(loanRepository.findAcceptedByWarId(warId)).extracting(ClanWarLoan::getId).containsExactly(accepted);
        assertThat(loanRepository.findOpenByLendingClanId(owls.getId())).hasSize(2);
        assertThat(loanRepository.findPage(warId, null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(3);
        assertThat(loanRepository.findPage(null, member.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(ClanWarLoan::getId).containsExactly(pending);
        assertThat(loanRepository.findWithRefsById(pending)).isPresent();
        assertThat(allianceRepository.findActiveBetween(lapiz.getId(), owls.getId())).isPresent();

        assertThat(loanRepository.closeOpenForWar(warId, Instant.now())).isEqualTo(2);
        entityManager.clear();
        assertThat(loanRepository.findById(accepted)).get().satisfies(l -> {
            assertThat(l.getStatus()).isEqualTo(ClanWarLoanStatus.ended);
            assertThat(l.getEndedAt()).isNotNull();
        });
        assertThat(loanRepository.findById(pending)).get().satisfies(l -> {
            assertThat(l.getStatus()).isEqualTo(ClanWarLoanStatus.cancelled);
            assertThat(l.getEndedAt()).isNull();
            assertThat(l.getResolvedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("settlement reads participants biggest contribution first, pays each once and finds unsettled wars")
    void settlementLookups() {
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID warId = war(current, owls, lapiz);
        String participant = "INSERT INTO clan_war_participants (war_id, user_id, clan_id, standing_weight, guard, "
                + "contribution) VALUES (?1, ?2, ?3, 0.5, 100, ?4)";
        entityManager.createNativeQuery(participant).setParameter(1, warId).setParameter(2, member.getId())
                .setParameter(3, owls.getId()).setParameter(4, 10.0).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, warId).setParameter(2, founder.getId())
                .setParameter(3, owls.getId()).setParameter(4, 90.0).executeUpdate();
        warState(warId, "status = 'ended', outcome = 'attacker_won', ended_at = ?1", Instant.now());
        Item crate = clanItem("crate", "War Crate");
        entityManager.persist(ClanWarRewardItem.builder().item(crate).build());
        entityManager.persist(ClanWarRewardItem.builder().item(clanItem("crate", "Retired Crate")).active(false).build());
        entityManager.flush();

        assertThat(participantRepository.findByWarIdInContributionOrder(warId))
                .extracting(p -> p.getUser().getId()).containsExactly(founder.getId(), member.getId());
        assertThat(warRepository.findEndedUnsettled(10)).containsExactly(warId);
        assertThat(participantRepository.markRewarded(warId, founder.getId(), 45.0, Instant.now())).isEqualTo(1);
        assertThat(participantRepository.markRewarded(warId, founder.getId(), 45.0, Instant.now())).isZero();
        assertThat(participantRepository.markRewarded(warId, member.getId(), 5.0, Instant.now())).isEqualTo(1);
        assertThat(warRepository.findEndedUnsettled(10)).isEmpty();
        entityManager.clear();
        assertThat(rewardItemRepository.findActiveWithItems()).singleElement()
                .satisfies(reward -> assertThat(reward.getItem().getType().getKey()).isEqualTo("crate"));
        assertThat(rewardItemRepository.findAllWithItems()).extracting(ClanWarRewardItem::isActive)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("a player's war record counts wars fought and won, hits, breaks both ways, Standing moved and war XP")
    void playerWarStats() {
        MapDifficulty difficulty = rankedAt(6.0);
        UUID current = season(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS));
        UUID won = war(current, owls, lapiz);
        UUID picking = war(current, lapiz, owls);
        User lapizStar = user(76561190000000312L, "Lapiz Star");
        String participant = "INSERT INTO clan_war_participants (war_id, user_id, clan_id, standing_weight, guard, "
                + "contribution, xp_awarded, rewarded_at) VALUES (?1, ?2, ?3, 1, 100, ?4, ?5, ?6)";
        entityManager.createNativeQuery(participant).setParameter(1, won).setParameter(2, founder.getId())
                .setParameter(3, owls.getId()).setParameter(4, 75.0).setParameter(5, 40.0)
                .setParameter(6, Instant.now()).executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, won).setParameter(2, lapizStar.getId())
                .setParameter(3, lapiz.getId()).setParameter(4, 10.0).setParameter(5, null).setParameter(6, null)
                .executeUpdate();
        entityManager.createNativeQuery(participant).setParameter(1, picking).setParameter(2, founder.getId())
                .setParameter(3, owls.getId()).setParameter(4, 5.0).setParameter(5, null).setParameter(6, null)
                .executeUpdate();
        warState(won, "status = 'ended', outcome = 'attacker_won', ended_at = ?1", Instant.now());
        score(founder, difficulty, 0.0, Instant.now());
        score(lapizStar, difficulty, 0.0, Instant.now());
        entityManager.flush();
        String scoreOf = "SELECT id FROM scores WHERE user_id = ?1";
        UUID founderScore = (UUID) entityManager.createNativeQuery(scoreOf).setParameter(1, founder.getId())
                .getSingleResult();
        UUID starScore = (UUID) entityManager.createNativeQuery(scoreOf).setParameter(1, lapizStar.getId())
                .getSingleResult();
        String hit = "INSERT INTO clan_war_hits (war_id, attacker_user_id, victim_user_id, victim_cycle, "
                + "map_difficulty_id, attacker_score_id, victim_score_id, damage, guard_after, broke, standing_moved, "
                + "xp_awarded) VALUES (?1, ?2, ?3, 0, ?4, ?5, ?6, 50, 0, ?7, ?8, ?9)";
        entityManager.createNativeQuery(hit).setParameter(1, won).setParameter(2, founder.getId())
                .setParameter(3, lapizStar.getId()).setParameter(4, difficulty.getId()).setParameter(5, founderScore)
                .setParameter(6, starScore).setParameter(7, true).setParameter(8, 12.5).setParameter(9, 200.0)
                .executeUpdate();
        entityManager.createNativeQuery(hit).setParameter(1, won).setParameter(2, lapizStar.getId())
                .setParameter(3, founder.getId()).setParameter(4, difficulty.getId()).setParameter(5, starScore)
                .setParameter(6, null).setParameter(7, false).setParameter(8, 0.0).setParameter(9, null)
                .executeUpdate();
        entityManager.createNativeQuery(hit).setParameter(1, won).setParameter(2, lapizStar.getId())
                .setParameter(3, founder.getId()).setParameter(4, difficulty.getId()).setParameter(5, starScore)
                .setParameter(6, founderScore).setParameter(7, true).setParameter(8, 0.0).setParameter(9, null)
                .executeUpdate();

        ClanWarParticipantRepository.PlayerWarStatsView mine = participantRepository.findPlayerWarStats(founder.getId());
        ClanWarParticipantRepository.PlayerWarStatsView theirs = participantRepository
                .findPlayerWarStats(lapizStar.getId());
        ClanWarParticipantRepository.PlayerWarStatsView nobody = participantRepository.findPlayerWarStats(member.getId());

        assertThat(mine.getWarsFought()).isEqualTo(1);
        assertThat(mine.getWarsWon()).isEqualTo(1);
        assertThat(mine.getHits()).isEqualTo(1);
        assertThat(mine.getBreaksDealt()).isEqualTo(1);
        assertThat(mine.getBreaksSuffered()).isEqualTo(1);
        assertThat(mine.getStandingMoved()).isEqualTo(12.5);
        assertThat(mine.getContribution()).isEqualTo(80.0);
        assertThat(mine.getWarXp()).isEqualTo(240.0);
        assertThat(theirs.getWarsWon()).isZero();
        assertThat(theirs.getHits()).isEqualTo(2);
        assertThat(theirs.getBreaksSuffered()).isEqualTo(1);
        assertThat(nobody.getWarsFought()).isZero();
        assertThat(nobody.getWarXp()).isZero();
    }

    @Test
    @DisplayName("a new level cosmetic reaches every active clan already past that level's XP, once")
    void levelItemCatchUp() {
        Item emblem = clanItem("clan_emblem", "Late Emblem");
        Clan gone = clan("Gone", "GON", "gone");
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE clans SET total_xp = 500 WHERE id IN (?1, ?2)")
                .setParameter(1, owls.getId()).setParameter(2, gone.getId()).executeUpdate();
        entityManager.createNativeQuery("UPDATE clans SET active = false WHERE id = ?1")
                .setParameter(1, gone.getId()).executeUpdate();

        assertThat(clanItemRepository.grantToClansAtLevel(emblem.getId(), 2, 300.0)).isEqualTo(1);
        assertThat(clanItemRepository.grantToClansAtLevel(emblem.getId(), 2, 300.0)).isZero();
        assertThat(clanItemRepository.existsByClan_IdAndItem_Id(owls.getId(), emblem.getId())).isTrue();
        assertThat(clanItemRepository.existsByClan_IdAndItem_Id(lapiz.getId(), emblem.getId())).isFalse();
    }

    @Test
    @DisplayName("the clan ref cache loads open members and equipped cosmetics of active clans only, types fetched")
    void clanRefCacheLoads() {
        Clan gone = clan("Gone", "GON", "gone");
        User ghost = user(76561190000000313L, "Ghost");
        seat(gone, ghost, ClanRole.founder, Instant.now());
        Item emblem = clanItem("clan_emblem", "Owl Emblem");
        Item goneEmblem = clanItem("clan_emblem", "Gone Emblem");
        entityManager.persist(ClanItem.builder().clan(owls).item(emblem).source(ClanItemSource.manual).build());
        entityManager.persist(ClanItem.builder().clan(gone).item(goneEmblem).source(ClanItemSource.manual).build());
        entityManager.flush();
        entityManager.persist(ClanEquippedItem.builder().clan(owls).itemType(emblem.getType()).item(emblem).build());
        entityManager.persist(ClanEquippedItem.builder().clan(gone).itemType(goneEmblem.getType()).item(goneEmblem)
                .build());
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE clans SET active = false WHERE id = ?1")
                .setParameter(1, gone.getId()).executeUpdate();
        entityManager.createNativeQuery("UPDATE clan_members SET left_at = now(), leave_reason = 'left' WHERE user_id = ?1")
                .setParameter(1, member.getId()).executeUpdate();
        entityManager.clear();

        assertThat(memberRepository.findAllOpenInActiveClans())
                .extracting(m -> m.getUser().getId())
                .containsExactlyInAnyOrder(founder.getId(), commander.getId(), officer.getId());
        assertThat(equippedRepository.findAllOfActiveClans()).singleElement().satisfies(e -> {
            assertThat(Hibernate.isInitialized(e.getItem().getType())).isTrue();
            assertThat(e.getItem().getName()).isEqualTo("Owl Emblem");
        });
    }

    @Test
    @DisplayName("clan notifications are allowed by the notification type check")
    void clanNotificationTypes() {
        for (String type : List.of("clan_membership", "clan_alliance", "clan_war")) {
            entityManager.createNativeQuery("INSERT INTO notifications (user_id, type, title) VALUES (?1, ?2, 'x')")
                    .setParameter(1, member.getId()).setParameter(2, type).executeUpdate();
        }

        assertThat(entityManager.createNativeQuery("SELECT COUNT(*) FROM notifications WHERE user_id = ?1")
                .setParameter(1, member.getId()).getSingleResult()).isEqualTo(3L);
    }
}
