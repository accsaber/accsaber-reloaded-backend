package com.accsaber.backend.repository.clan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.clan.Clan;
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
import com.accsaber.backend.repository.clan.war.ClanWarParticipantRepository;

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
        UUID low = (UUID) entityManager.createNativeQuery("SELECT LEAST(CAST(?1 AS uuid), CAST(?2 AS uuid))")
                .setParameter(1, owls.getId()).setParameter(2, lapiz.getId()).getSingleResult();
        UUID high = low.equals(owls.getId()) ? lapiz.getId() : owls.getId();
        entityManager.createNativeQuery("INSERT INTO clan_alliances (clan_a_id, clan_b_id, proposed_by_clan_id, "
                + "proposed_by_user_id, status, accepted_at) VALUES (?1, ?2, ?1, ?3, 'active', NOW())")
                .setParameter(1, low).setParameter(2, high).setParameter(3, founder.getId()).executeUpdate();
        entityManager.flush();

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
    @DisplayName("season contributors sum a player's war contribution for their own clan, biggest first")
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

        List<ClanWarParticipantRepository.ContributorView> contributors = participantRepository
                .findSeasonContributors(current, owls.getId());

        assertThat(contributors).extracting(ClanWarParticipantRepository.ContributorView::getUserId)
                .containsExactly(officer.getId(), founder.getId());
        assertThat(contributors.get(0).getContribution()).isEqualTo(70.0);
    }
}
