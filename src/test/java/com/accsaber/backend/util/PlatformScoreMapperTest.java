package com.accsaber.backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderScoreResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberScoreResponse;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;

class PlatformScoreMapperTest {

    private static final UUID MAP_DIFF_ID = UUID.randomUUID();
    private static final Long USER_ID = 76561198012345678L;
    private static final UUID NF_ID = UUID.randomUUID();
    private static final UUID DA_ID = UUID.randomUUID();
    private static final Map<String, UUID> MODIFIER_MAP = Map.of("NF", NF_ID, "DA", DA_ID);

    @Nested
    class FromBeatLeader {

        @Test
        void mapsAllFields() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getMapDifficultyId()).isEqualTo(MAP_DIFF_ID);
            assertThat(result.getScore()).isEqualTo(950000);
            assertThat(result.getScoreNoMods()).isEqualTo(900000);
            assertThat(result.getRank()).isEqualTo(5);
            assertThat(result.getRankWhenSet()).isEqualTo(5);
            assertThat(result.getBlScoreId()).isEqualTo(123456L);
            assertThat(result.getMaxCombo()).isEqualTo(500);
            assertThat(result.getBadCuts()).isEqualTo(3);
            assertThat(result.getMisses()).isEqualTo(2);
            assertThat(result.getWallHits()).isEqualTo(1);
            assertThat(result.getBombHits()).isEqualTo(0);
            assertThat(result.getPauses()).isEqualTo(1);
            assertThat(result.getStreak115()).isEqualTo(200);
            assertThat(result.getPlayCount()).isEqualTo(10);
            assertThat(result.getHmd()).isEqualTo("64");
            assertThat(result.getTimeSet()).isEqualTo(Instant.ofEpochSecond(1700000000L));
        }

        @Test
        void resolvesModifiersFromCommaString() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setModifiers("NF,DA");

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getModifierIds()).containsExactlyInAnyOrder(NF_ID, DA_ID);
        }

        @Test
        void emptyModifierString_yieldsEmptyList() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setModifiers("");

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getModifierIds()).isEmpty();
        }

        @Test
        void nullModifierString_yieldsEmptyList() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setModifiers(null);

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getModifierIds()).isEmpty();
        }

        @Test
        void unknownModifierCode_isSkipped() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setModifiers("NF,UNKNOWN,DA");

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getModifierIds()).containsExactlyInAnyOrder(NF_ID, DA_ID);
        }

        @Test
        void nullMaxCombo_mapsToNull() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setMaxCombo(null);

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getMaxCombo()).isNull();
        }

        @Test
        void zeroMaxCombo_mapsToNull() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setMaxCombo(0);

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getMaxCombo()).isNull();
        }

        @Test
        void nullPlayCount_mapsToNull() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setPlayCount(null);

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getPlayCount()).isNull();
        }

        @Test
        void zeroPlayCount_mapsToNull() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setPlayCount(0);

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getPlayCount()).isNull();
        }

        @Test
        void nullHmd_mapsToNull() {
            BeatLeaderScoreResponse bl = buildBeatLeaderScore();
            bl.setHmd(null);

            SubmitScoreRequest result = PlatformScoreMapper.fromBeatLeader(bl, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getHmd()).isNull();
        }
    }

    @Nested
    class HasBannedModifier {

        @Test
        void returnsFalse_forNull() {
            assertThat(PlatformScoreMapper.hasBannedModifier(null)).isFalse();
        }

        @Test
        void returnsFalse_forBlank() {
            assertThat(PlatformScoreMapper.hasBannedModifier("")).isFalse();
        }

        @Test
        void returnsFalse_forAllowedModifiers() {
            assertThat(PlatformScoreMapper.hasBannedModifier("NF,DA,GN")).isFalse();
        }

        @Test
        void returnsTrue_forEachBannedCode() {
            for (String code : PlatformScoreMapper.BANNED_MODIFIER_CODES) {
                assertThat(PlatformScoreMapper.hasBannedModifier(code))
                        .as("Expected banned for code: " + code)
                        .isTrue();
            }
        }

        @Test
        void returnsTrue_whenBannedMixedWithAllowed() {
            assertThat(PlatformScoreMapper.hasBannedModifier("NF,SF")).isTrue();
        }
    }

    @Nested
    class FromScoreSaber {

        @Test
        void mapsFieldsAndLeavesBlExclusiveFieldsNull() {
            ScoreSaberScoreResponse ss = buildScoreSaberScore();

            SubmitScoreRequest result = PlatformScoreMapper.fromScoreSaber(ss, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getMapDifficultyId()).isEqualTo(MAP_DIFF_ID);
            assertThat(result.getScore()).isEqualTo(940000);
            assertThat(result.getScoreNoMods()).isEqualTo(890000);
            assertThat(result.getRank()).isEqualTo(7);
            assertThat(result.getMaxCombo()).isEqualTo(480);
            assertThat(result.getBadCuts()).isEqualTo(4);
            assertThat(result.getMisses()).isEqualTo(3);
            assertThat(result.getBlScoreId()).isNull();
            assertThat(result.getWallHits()).isNull();
            assertThat(result.getBombHits()).isNull();
            assertThat(result.getPauses()).isNull();
            assertThat(result.getStreak115()).isNull();
            assertThat(result.getPlayCount()).isNull();
            assertThat(result.getHmd()).isEqualTo("Valve Index");
            assertThat(result.getTimeSet()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        }

        @Test
        void resolvesModifiers() {
            ScoreSaberScoreResponse ss = buildScoreSaberScore();
            ss.setModifiers("NF");

            SubmitScoreRequest result = PlatformScoreMapper.fromScoreSaber(ss, MAP_DIFF_ID, USER_ID, MODIFIER_MAP);

            assertThat(result.getModifierIds()).containsExactly(NF_ID);
        }
    }

    private BeatLeaderScoreResponse buildBeatLeaderScore() {
        BeatLeaderScoreResponse bl = new BeatLeaderScoreResponse();
        bl.setId(123456L);
        bl.setModifiedScore(950000);
        bl.setBaseScore(900000);
        bl.setRank(5);
        bl.setMaxCombo(500);
        bl.setBadCuts(3);
        bl.setMissedNotes(2);
        bl.setWallsHit(1);
        bl.setBombCuts(0);
        bl.setPauses(1);
        bl.setMaxStreak(200);
        bl.setPlayCount(10);
        bl.setHmd(64);
        bl.setLeaderboardId("bl_123");
        bl.setTimepost(1700000000L);
        bl.setModifiers("");
        BeatLeaderScoreResponse.Player player = new BeatLeaderScoreResponse.Player();
        player.setId(String.valueOf(USER_ID));
        bl.setPlayer(player);
        return bl;
    }

    private ScoreSaberScoreResponse buildScoreSaberScore() {
        ScoreSaberScoreResponse ss = new ScoreSaberScoreResponse();
        ss.setId(789012L);
        ss.setModifiedScore(940000);
        ss.setBaseScore(890000);
        ss.setRank(7);
        ss.setMaxCombo(480);
        ss.setBadCuts(4);
        ss.setMissedNotes(3);
        ss.setDeviceHmd("Valve Index");
        ss.setModifiers("");
        ss.setTimeSet("2024-01-01T00:00:00Z");
        ScoreSaberScoreResponse.LeaderboardPlayerInfo info = new ScoreSaberScoreResponse.LeaderboardPlayerInfo();
        info.setId(String.valueOf(USER_ID));
        ss.setLeaderboardPlayerInfo(info);
        return ss;
    }
}
