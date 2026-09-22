package com.accsaber.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClanSchemaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;

    private User alice;
    private User bob;

    @BeforeEach
    void seed() {
        alice = persistUser(76561190000000201L, "Alice");
        bob = persistUser(76561190000000202L, "Bob");
        entityManager.flush();
    }

    private User persistUser(Long id, String name) {
        User user = User.builder().id(id).name(name).country("ES").build();
        entityManager.persist(user);
        return user;
    }

    private int sql(String statement, Object... params) {
        Query query = entityManager.createNativeQuery(statement);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return query.executeUpdate();
    }

    private Object single(String statement, Object... params) {
        Query query = entityManager.createNativeQuery(statement);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return query.getSingleResult();
    }

    private UUID clan(String name, String tag) {
        return (UUID) single("INSERT INTO clans (name, tag, slug) VALUES (?1, ?2, ?3) RETURNING id",
                name, tag, name.toLowerCase());
    }

    private UUID season(String slug, String startsAt, String endsAt) {
        return (UUID) single("INSERT INTO clan_seasons (name, slug, starts_at, ends_at) "
                + "VALUES (?1, ?1, CAST(?2 AS timestamptz), CAST(?3 AS timestamptz)) RETURNING id",
                slug, startsAt, endsAt);
    }

    private UUID item(String typeKey, String name) {
        return (UUID) single("INSERT INTO items (type_id, name, serialized) "
                + "VALUES ((SELECT id FROM item_types WHERE key = ?1), ?2, false) RETURNING id", typeKey, name);
    }

    private UUID itemType(String key) {
        return (UUID) single("SELECT id FROM item_types WHERE key = ?1", key);
    }

    @Test
    @DisplayName("V158 seeds the clan curves, cosmetic types, starting capacities and launch war modes")
    void seedsArePresent() {
        assertThat(((Number) single("SELECT COUNT(*) FROM curves WHERE id IN "
                + "('acc00000-0000-0000-0000-000000000030', 'acc00000-0000-0000-0000-000000000031')")).intValue())
                .isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<String> children = entityManager.createNativeQuery("SELECT child.key FROM item_types child "
                + "JOIN item_types parent ON parent.id = child.parent_type_id "
                + "WHERE parent.key = 'clan_cosmetic' ORDER BY child.key").getResultList();
        assertThat(children).containsExactly("clan_banner", "clan_tag_card", "clan_title_effect");
        assertThat(((Number) single("SELECT amount FROM clan_level_capacities "
                + "WHERE level = 0 AND capacity = 'member_slots'")).intValue()).isEqualTo(10);
        assertThat(((Number) single("SELECT COUNT(*) FROM clan_level_war_modes WHERE level = 0")).intValue())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("clan cosmetic types reuse the border colour, background and title render contracts")
    void cosmeticTypesReuseRenderContracts() {
        assertThat(single("SELECT (SELECT value_schema FROM item_types WHERE key = 'clan_tag_card') = "
                + "(SELECT value_schema FROM item_types WHERE key = 'profile_border_color')")).isEqualTo(true);
        assertThat(single("SELECT COUNT(*) FROM item_types WHERE key = 'clan_card'")).isEqualTo(0L);
        assertThat(single("SELECT (SELECT value_schema FROM item_types WHERE key = 'clan_banner') = "
                + "(SELECT value_schema FROM item_types WHERE key = 'profile_background')")).isEqualTo(true);
        assertThat(single("SELECT jsonb_exists(value_schema -> 'properties', 'text') FROM item_types "
                + "WHERE key = 'clan_title_effect'")).isEqualTo(false);
        assertThat(single("SELECT CAST(value_schema -> 'required' AS text) FROM item_types "
                + "WHERE key = 'clan_title_effect'")).isEqualTo("[\"states\"]");
    }

    @Test
    @DisplayName("a tag has to be two to five uppercase letters or digits")
    void tagShapeIsEnforced() {
        clan("Valid", "AB12");

        assertThatThrownBy(() -> clan("Lowercase", "abc")).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a tag colour is a lowercase six digit hex or nothing")
    void tagColourShapeIsEnforced() {
        UUID clan = clan("Painted", "PNT");
        sql("UPDATE clans SET tag_color = '#22c55e' WHERE id = ?1", clan);

        assertThatThrownBy(() -> sql("UPDATE clans SET tag_color = 'green' WHERE id = ?1", clan))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("two active clans cannot share a tag, but a disbanded one frees it")
    void activeTagsAreUnique() {
        UUID first = clan("First", "DUP");
        sql("UPDATE clans SET active = false WHERE id = ?1", first);
        clan("Second", "DUP");

        assertThatThrownBy(() -> clan("Third", "DUP")).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a player holds one open membership, and leaving frees them to join again")
    void oneOpenMembershipPerPlayer() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        sql("INSERT INTO clan_members (clan_id, user_id) VALUES (?1, ?2)", red, alice.getId());
        sql("UPDATE clan_members SET left_at = NOW(), leave_reason = 'left' WHERE user_id = ?1", alice.getId());
        sql("INSERT INTO clan_members (clan_id, user_id) VALUES (?1, ?2)", blue, alice.getId());

        assertThatThrownBy(() -> sql("INSERT INTO clan_members (clan_id, user_id) VALUES (?1, ?2)", red, alice.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("leaving needs a reason and a reason needs a leave time")
    void leaveReasonTravelsWithLeftAt() {
        UUID red = clan("Red", "RED");

        assertThatThrownBy(() -> sql("INSERT INTO clan_members (clan_id, user_id, left_at) VALUES (?1, ?2, NOW())",
                red, alice.getId())).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a clan has one open founder")
    void oneFounderPerClan() {
        UUID red = clan("Red", "RED");
        sql("INSERT INTO clan_members (clan_id, user_id, role) VALUES (?1, ?2, 'founder')", red, alice.getId());

        assertThatThrownBy(() -> sql("INSERT INTO clan_members (clan_id, user_id, role) VALUES (?1, ?2, 'founder')",
                red, bob.getId())).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a clan can only equip a cosmetic it owns, in the slot of that cosmetic's type")
    void equippedCosmeticMustBeOwnedAndMatchItsSlot() {
        UUID red = clan("Red", "RED");
        UUID card = item("clan_tag_card", "Red Card");
        sql("INSERT INTO clan_items (clan_id, item_id, source) VALUES (?1, ?2, 'manual')", red, card);
        sql("INSERT INTO clan_equipped_items (clan_id, item_type_id, item_id) VALUES (?1, ?2, ?3)",
                red, itemType("clan_tag_card"), card);

        assertThatThrownBy(() -> sql("INSERT INTO clan_equipped_items (clan_id, item_type_id, item_id) "
                + "VALUES (?1, ?2, ?3)", red, itemType("clan_banner"), card))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("equipping something the clan never got is rejected")
    void unownedCosmeticCannotBeEquipped() {
        UUID red = clan("Red", "RED");
        UUID banner = item("clan_banner", "Stolen Banner");

        assertThatThrownBy(() -> sql("INSERT INTO clan_equipped_items (clan_id, item_type_id, item_id) "
                + "VALUES (?1, ?2, ?3)", red, itemType("clan_banner"), banner))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("seasons cannot overlap")
    void seasonsDoNotOverlap() {
        season("season-1", "2026-01-01T00:00:00Z", "2026-07-01T00:00:00Z");
        season("season-2", "2026-07-01T00:00:00Z", "2027-01-01T00:00:00Z");

        assertThatThrownBy(() -> season("overlap", "2026-06-01T00:00:00Z", "2026-08-01T00:00:00Z"))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("earned Standing can never go negative")
    void earnedStandingHasAFloor() {
        UUID red = clan("Red", "RED");
        UUID current = season("season-1", "2026-01-01T00:00:00Z", "2026-07-01T00:00:00Z");

        assertThatThrownBy(() -> sql("INSERT INTO clan_season_standings (season_id, clan_id, earned) "
                + "VALUES (?1, ?2, -1)", current, red)).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("an XP grant lands once per clan, source and source id")
    void xpGrantsAreIdempotent() {
        UUID red = clan("Red", "RED");
        String grant = "INSERT INTO clan_xp_grants (clan_id, source, source_id, raw_amount, roster_factor, amount) "
                + "VALUES (?1, 'daily_play', '2026-09-13', 100, 2, 50)";
        sql(grant, red);

        assertThatThrownBy(() -> sql(grant, red)).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a clan holds one open attack, and ending it frees the slot")
    void oneOpenAttackPerClan() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        UUID green = clan("Green", "GRN");
        UUID current = season("season-1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        String declare = "INSERT INTO clan_wars (season_id, attacker_clan_id, defender_clan_id, declared_by, arena, "
                + "arena_spec, ruleset) VALUES (?1, ?2, ?3, ?4, 'mixed', CAST('{\"poolSize\":10}' AS jsonb), 'duel')";
        sql(declare, current, red, blue, alice.getId());
        sql("UPDATE clan_wars SET status = 'ended', outcome = 'retreated', ended_at = NOW() WHERE attacker_clan_id = ?1",
                red);
        sql(declare, current, red, blue, alice.getId());

        assertThatThrownBy(() -> sql(declare, current, red, green, alice.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("an ended war has to carry both its outcome and its end time")
    void endedWarCarriesOutcome() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        UUID current = season("season-1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");

        assertThatThrownBy(() -> sql("INSERT INTO clan_wars (season_id, attacker_clan_id, defender_clan_id, "
                + "declared_by, arena, arena_spec, ruleset, status) VALUES (?1, ?2, ?3, ?4, 'mixed', "
                + "CAST('{}' AS jsonb), 'duel', 'ended')", current, red, blue, alice.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a participant must fight for one of the two sides of that war")
    void participantBelongsToASide() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        UUID outsider = clan("Outsider", "OUT");
        UUID current = season("season-1", "2026-01-01T00:00:00Z", "2027-01-01T00:00:00Z");
        UUID war = (UUID) single("INSERT INTO clan_wars (season_id, attacker_clan_id, defender_clan_id, declared_by, "
                + "arena, arena_spec, ruleset) VALUES (?1, ?2, ?3, ?4, 'mixed', CAST('{}' AS jsonb), 'duel') "
                + "RETURNING id", current, red, blue, alice.getId());
        sql("INSERT INTO clan_war_sides (war_id, clan_id, stake, stake_remaining, standing_at_declare) "
                + "VALUES (?1, ?2, 100, 100, 100)", war, red);
        sql("INSERT INTO clan_war_participants (war_id, user_id, clan_id, standing_weight, guard) "
                + "VALUES (?1, ?2, ?3, 1, 100)", war, alice.getId(), red);

        assertThatThrownBy(() -> sql("INSERT INTO clan_war_participants (war_id, user_id, clan_id, standing_weight, "
                + "guard) VALUES (?1, ?2, ?3, 1, 100)", war, bob.getId(), outsider))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("an alliance is stored once as an ordered pair")
    void allianceIsAnOrderedPair() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        UUID low = (UUID) single("SELECT LEAST(CAST(?1 AS uuid), CAST(?2 AS uuid))", red, blue);
        UUID high = (UUID) single("SELECT GREATEST(CAST(?1 AS uuid), CAST(?2 AS uuid))", red, blue);

        assertThatThrownBy(() -> sql("INSERT INTO clan_alliances (clan_a_id, clan_b_id, proposed_by_clan_id, "
                + "proposed_by_user_id) VALUES (?1, ?2, ?1, ?3)", high, low, alice.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    private UUID missionTemplate(String code, String type, String targets) {
        return (UUID) single("INSERT INTO mission_templates (code, name, description, type, pool, event_targets) "
                + "VALUES (?1, ?1, 'Clan work', ?2, 'clan', CAST(?3 AS jsonb)) RETURNING id", code, type, targets);
    }

    private static final String CLAN_MISSION = "INSERT INTO user_missions (template_id, pool, clan_id, user_id, parent_mission_id, "
            + "expires_at) VALUES (?1, ?2, ?3, ?4, ?5, NOW() + INTERVAL '7 days') RETURNING id";

    @Test
    @DisplayName("a fixed target mission type needs its targets before it can join the clan pool")
    void clanFixedTargetTemplatesNeedTargets() {
        missionTemplate("clan-scores", "SCORES_N", null);
        missionTemplate("clan-ap-gain", "AP_GAIN_OVERALL", "{\"ap\": 20}");

        assertThatThrownBy(() -> missionTemplate("clan-ap-gain-bare", "AP_GAIN_OVERALL", null))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("clan rows carry their clan, shared rows have no player and member rows point at their parent")
    void clanMissionRowsKeepTheirShape() {
        UUID red = clan("Red", "RED");
        UUID template = missionTemplate("clan-scores", "SCORES_N", null);
        UUID parent = (UUID) single(CLAN_MISSION, template, "clan", red, null, null);
        single(CLAN_MISSION, template, "clan", red, alice.getId(), parent);

        assertThatThrownBy(() -> single(CLAN_MISSION, template, "clan", null, null, null))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a clan member row without a player, or a player on a clan row without a parent, is rejected")
    void clanMemberRowsNeedBothPlayerAndParent() {
        UUID red = clan("Red", "RED");
        UUID template = missionTemplate("clan-scores", "SCORES_N", null);
        UUID parent = (UUID) single(CLAN_MISSION, template, "clan", red, null, null);

        assertThatThrownBy(() -> single(CLAN_MISSION, template, "clan", red, null, parent))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a player on a shared clan row is rejected")
    void sharedClanRowsHaveNoPlayer() {
        UUID red = clan("Red", "RED");
        UUID template = missionTemplate("clan-scores", "SCORES_N", null);

        assertThatThrownBy(() -> single(CLAN_MISSION, template, "clan", red, alice.getId(), null))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a clan opens one mission per template at a time and each member holds one row under it")
    void oneOpenClanMissionPerTemplateAndOneRowPerMember() {
        UUID red = clan("Red", "RED");
        UUID template = missionTemplate("clan-scores", "SCORES_N", null);
        UUID parent = (UUID) single(CLAN_MISSION, template, "clan", red, null, null);
        single(CLAN_MISSION, template, "clan", red, alice.getId(), parent);

        assertThatThrownBy(() -> single(CLAN_MISSION, template, "clan", red, alice.getId(), parent))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("two open shared rows for the same template in one clan are rejected")
    void twoOpenClanParentsAreRejected() {
        UUID red = clan("Red", "RED");
        UUID template = missionTemplate("clan-scores", "SCORES_N", null);
        single(CLAN_MISSION, template, "clan", red, null, null);

        assertThatThrownBy(() -> single(CLAN_MISSION, template, "clan", red, null, null))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a clan rivals another clan once and never itself")
    void rivalsArePairsOfTwoClans() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        String rival = "INSERT INTO clan_rivals (clan_id, rival_clan_id) VALUES (?1, ?2)";
        sql(rival, red, blue);
        sql(rival, blue, red);

        assertThatThrownBy(() -> sql(rival, red, red)).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a rivalry is stored once per direction")
    void rivalryIsUniquePerDirection() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        String rival = "INSERT INTO clan_rivals (clan_id, rival_clan_id) VALUES (?1, ?2)";
        sql(rival, red, blue);

        assertThatThrownBy(() -> sql(rival, red, blue)).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a chat row belongs to exactly one channel")
    void chatRowsBelongToOneChannel() {
        UUID red = clan("Red", "RED");
        sql("INSERT INTO chat_messages (clan_id, user_id, content) VALUES (?1, ?2, 'hi')", red, alice.getId());

        assertThatThrownBy(() -> sql("INSERT INTO chat_messages (user_id, content) VALUES (?1, 'nowhere')",
                alice.getId())).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a clan event row carries an event and no text, and needs no author")
    void eventRowsHaveNoText() {
        UUID red = clan("Red", "RED");
        sql("INSERT INTO chat_messages (clan_id, event, subject_user_id) VALUES (?1, 'member_left', ?2)",
                red, alice.getId());

        assertThatThrownBy(() -> sql("INSERT INTO chat_messages (clan_id, user_id, content, event) "
                + "VALUES (?1, ?2, 'both', 'member_left')", red, alice.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("a said message needs its author")
    void saidMessagesNeedAnAuthor() {
        UUID red = clan("Red", "RED");

        assertThatThrownBy(() -> sql("INSERT INTO chat_messages (clan_id, content) VALUES (?1, 'who said this')",
                red)).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("only a war event can point a chat row at a war")
    void onlyWarEventsCarryAWar() {
        UUID red = clan("Red", "RED");
        UUID blue = clan("Blue", "BLUE");
        UUID current = season("season-1", "2026-01-01T00:00:00Z", "2099-07-01T00:00:00Z");
        UUID war = (UUID) single("INSERT INTO clan_wars (season_id, attacker_clan_id, defender_clan_id, declared_by, "
                + "arena, arena_spec, ruleset) VALUES (?1, ?2, ?3, ?4, 'mixed', CAST('{}' AS jsonb), 'duel') RETURNING id",
                current, red, blue, alice.getId());
        sql("INSERT INTO chat_messages (clan_id, event, subject_clan_id, war_id) VALUES (?1, 'war_declared', ?2, ?3)",
                red, blue, war);

        assertThatThrownBy(() -> sql("INSERT INTO chat_messages (clan_id, event, war_id) VALUES (?1, 'member_left', ?2)",
                red, war)).isInstanceOf(PersistenceException.class);
    }
}
