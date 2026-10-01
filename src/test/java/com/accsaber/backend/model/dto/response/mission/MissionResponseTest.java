package com.accsaber.backend.model.dto.response.mission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionStatus;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.model.entity.mission.MissionType;
import com.accsaber.backend.model.entity.mission.UserMission;

class MissionResponseTest {

    @Test
    void apGainOverallExposesFractionalApAsTheNormalisedPair() {
        UserMission mission = mission(MissionType.AP_GAIN_OVERALL);
        mission.setProgressAp(2.4321);
        mission.setTargetAp(5.0);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getProgressValue()).isEqualByComparingTo(2.43);
        assertThat(response.getTargetValue()).isEqualByComparingTo(5.0);
    }

    @Test
    void apGainOverallMirrorsIntoTheCountPairThePluginReads() {
        UserMission mission = mission(MissionType.AP_GAIN_OVERALL);
        mission.setProgressAp(2.4321);
        mission.setTargetAp(5.0);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getProgressCount()).isEqualTo(2);
        assertThat(response.getTargetCount()).isEqualTo(5);
    }

    @Test
    void mirroredProgressNeverReadsAsCompleteBeforeItIs() {
        UserMission mission = mission(MissionType.AP_GAIN_OVERALL);
        mission.setProgressAp(4.99);
        mission.setTargetAp(5.0);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getProgressCount()).isEqualTo(4);
        assertThat(response.getTargetCount()).isEqualTo(5);
    }

    @Test
    void mirroringLeavesRealCountMissionsAlone() {
        UserMission mission = mission(MissionType.SCORES_N);
        mission.setProgressCount(3);
        mission.setTargetCount(10);
        mission.setTargetAp(5.0);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getProgressCount()).isEqualTo(3);
        assertThat(response.getTargetCount()).isEqualTo(10);
    }

    @Test
    void countTypesReportTheirCountPair() {
        UserMission mission = mission(MissionType.PLAY_N_MAPS);
        mission.setProgressCount(3);
        mission.setTargetCount(10);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getProgressValue()).isEqualByComparingTo(3.0);
        assertThat(response.getTargetValue()).isEqualByComparingTo(10.0);
    }

    @Test
    void xpWindowMeasuresProgressCountAgainstTargetXp() {
        UserMission mission = mission(MissionType.XP_IN_WINDOW);
        mission.setProgressCount(400);
        mission.setTargetXp(1000);
        mission.setTargetCount(null);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getProgressValue()).isEqualByComparingTo(400.0);
        assertThat(response.getTargetValue()).isEqualByComparingTo(1000.0);
    }

    @Test
    void binaryTypesHaveNoPairToDrawABarFrom() {
        UserMission mission = mission(MissionType.ACC_ON_MAP);
        mission.setTargetAcc(0.9700);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getProgressValue()).isNull();
        assertThat(response.getTargetValue()).isNull();
    }

    @Test
    void templateResponseCarriesTheTargetWithoutProgress() {
        Instant startsAt = Instant.now().minus(Duration.ofDays(8));
        Event event = Event.builder()
                .startsAt(startsAt)
                .endsAt(startsAt.plus(Duration.ofDays(28)))
                .build();
        MissionTemplate template = MissionTemplate.builder()
                .id(UUID.randomUUID())
                .code("alphas_end_w2_ap_gain")
                .name("We're going up")
                .description("Gain 5 AP overall.")
                .type(MissionType.AP_GAIN_OVERALL)
                .pool(MissionPool.event)
                .eventTargets(new EventMissionTargets(null, null, null, null, 5.0,
                        null, null, null, null, null, null, null, null, null))
                .build();

        MissionResponse response = MissionResponse.fromTemplate(template, event, Instant.now(),
                new MissionResponse.TargetContext(Map.of(), Map.of(), Map.of()));

        assertThat(response.getTargetValue()).isEqualByComparingTo(5.0);
        assertThat(response.getProgressValue()).isNull();
        assertThat(response.getTargetCount()).isEqualTo(5);
    }

    @Test
    void communityMissionsCarryTheSharedHeadcountAndYourOwnShare() {
        UserMission mission = mission(MissionType.SCORES_N);
        mission.setPool(MissionPool.community);
        mission.setProgressCount(3200);
        mission.setTargetCount(5000);

        MissionResponse response = MissionResponse.from(mission,
                new MissionResponse.SharedContext(
                        Map.of(mission.getId(), 412L),
                        Map.of(mission.getId(), 27.0)));

        assertThat(response.getProgressValue()).isEqualByComparingTo(3200.0);
        assertThat(response.getContributors()).isEqualTo(412L);
        assertThat(response.getYourContribution()).isEqualByComparingTo(27.0);
    }

    @Test
    void aCommunityMissionYouHaveNotTouchedReportsNoShareButStillCountsHeads() {
        UserMission mission = mission(MissionType.SCORES_N);
        mission.setPool(MissionPool.community);
        mission.setTargetCount(5000);

        MissionResponse response = MissionResponse.from(mission,
                new MissionResponse.SharedContext(Map.of(mission.getId(), 412L), Map.of()));

        assertThat(response.getContributors()).isEqualTo(412L);
        assertThat(response.getYourContribution()).isNull();
    }

    @Test
    void personalMissionsCarryNoCommunityFieldsAtAll() {
        UserMission mission = mission(MissionType.SCORES_N);
        mission.setProgressCount(3);
        mission.setTargetCount(10);

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getContributors()).isNull();
        assertThat(response.getYourContribution()).isNull();
    }

    @Test
    void aWeeklyCommunityMissionSaysWhichWeekItDiesWith() {
        Instant startsAt = Instant.now().minus(Duration.ofDays(20));
        Event event = Event.builder()
                .startsAt(startsAt)
                .endsAt(startsAt.plus(Duration.ofDays(42)))
                .build();
        UserMission mission = communityMission(event,
                startsAt.plus(Duration.ofDays(14)), startsAt.plus(Duration.ofDays(21)));

        MissionResponse response = MissionResponse.from(mission, MissionResponse.SharedContext.EMPTY);

        assertThat(response.getCode()).isEqualTo("accursed_totality_w3_community_streaks");
        assertThat(response.getWeek()).isEqualTo(3);
        assertThat(response.getEndsWithWeek()).isTrue();
    }

    @Test
    void anEventLongCommunityMissionSaysItDoesNotDieWithAWeek() {
        Instant startsAt = Instant.now().minus(Duration.ofDays(20));
        Event event = Event.builder()
                .startsAt(startsAt)
                .endsAt(startsAt.plus(Duration.ofDays(42)))
                .build();
        UserMission mission = communityMission(event, startsAt, null);

        MissionResponse response = MissionResponse.from(mission, MissionResponse.SharedContext.EMPTY);

        assertThat(response.getWeek()).isEqualTo(1);
        assertThat(response.getEndsWithWeek()).isFalse();
    }

    @Test
    void personalMissionsCarryNoSchedulingFieldsFromTheirTemplate() {
        UserMission mission = mission(MissionType.SCORES_N);
        mission.getTemplate().setCode("alphas_end_w1_pb");

        MissionResponse response = MissionResponse.from(mission);

        assertThat(response.getCode()).isNull();
        assertThat(response.getWeek()).isNull();
        assertThat(response.getEndsWithWeek()).isNull();
    }

    private UserMission communityMission(Event event, Instant unlocksAt, Instant completableUntil) {
        MissionTemplate template = MissionTemplate.builder()
                .code("accursed_totality_w3_community_streaks")
                .name("Beadwork")
                .description("The community racks up {count} total 115s together this week.")
                .type(MissionType.STREAK_SUM_N)
                .pool(MissionPool.community)
                .event(event)
                .unlocksAt(unlocksAt)
                .completableUntil(completableUntil)
                .build();
        return UserMission.builder()
                .id(UUID.randomUUID())
                .template(template)
                .pool(MissionPool.community)
                .status(MissionStatus.active)
                .targetCount(9000)
                .progressCount(0)
                .progressAp(0.0)
                .xpReward(250)
                .build();
    }

    private UserMission mission(MissionType type) {
        return UserMission.builder()
                .id(UUID.randomUUID())
                .template(MissionTemplate.builder().type(type).name(type.name())
                        .description(type.name()).build())
                .pool(MissionPool.event)
                .status(MissionStatus.active)
                .progressCount(0)
                .progressAp(0.0)
                .xpReward(0)
                .build();
    }
}
