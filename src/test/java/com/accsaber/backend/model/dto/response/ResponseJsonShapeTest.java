package com.accsaber.backend.model.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.accsaber.backend.config.PlainDoubleJackson2Module;
import com.accsaber.backend.config.PlainDoubleJacksonConfig;
import com.accsaber.backend.model.dto.response.campaign.CampaignBarrierResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignLeaderboardEntry;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressBroadcast;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarParticipantResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikeMapResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikePreviewResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarStrikeRulesResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.common.MapDifficultyRef;
import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.dto.response.item.TradeResponse;
import com.accsaber.backend.model.dto.response.map.BatchResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletedResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneCompletionResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneHolderResponse;
import com.accsaber.backend.model.dto.response.player.UserRelationResponse;
import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.model.dto.response.statistics.EventMissionLeaderboardResponse;
import com.accsaber.backend.model.dto.response.statistics.MapAvgApResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionCompletorResponse;
import com.accsaber.backend.model.dto.response.statistics.UserMapImprovementsResponse;
import com.accsaber.backend.model.entity.campaign.CampaignBackgroundPlacement;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ResponseJsonShapeTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final Instant T1 = Instant.parse("2026-01-02T03:04:05Z");
    private static final Instant T2 = Instant.parse("2026-02-03T04:05:06Z");
    private static final PublicClanResponse CLAN = new PublicClanResponse(C, "slug", "Clan", "TAG", "#112233",
            "#445566", "#778899", "icon", "banner", List.of());
    private static final PlayerRef PLAYER = new PlayerRef("76561198000000001", "Player", "avatar", "cdnAvatar",
            "DE", CLAN, null);
    private static final MapDifficultyRef MAP = new MapDifficultyRef(A, B, "Song", "Sub", "Artist", "Mapper",
            "cover", "cdnCover", Difficulty.EXPERT_PLUS, C, "True Acc");

    private static JsonMapper mapper;

    @BeforeAll
    static void loadAppMapper() {
        AtomicReference<JsonMapper> ref = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(PlainDoubleJacksonConfig.class)
                .withPropertyValues("spring.jackson.default-property-inclusion=non_null")
                .run(ctx -> ref.set(ctx.getBean(JsonMapper.class)));
        mapper = ref.get();
    }

    private static void assertSameJson(Object actual, Object legacy) {
        assertThat(mapper.readTree(mapper.writeValueAsString(actual)))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(legacy)));
    }

    private static JsonNode json(Object value) {
        return mapper.readTree(mapper.writeValueAsString(value));
    }

    private static void assertNestedPlayer(JsonNode json) {
        assertThat(json.get("player")).isEqualTo(json(PLAYER));
        assertThat(json.has("userId")).isFalse();
        assertThat(json.has("userName")).isFalse();
        assertThat(json.has("avatarUrl")).isFalse();
    }

    private static void assertNestedMap(JsonNode json) {
        assertThat(json.get("map")).isEqualTo(json(MAP));
        assertThat(json.get("map").get("id").asString()).isEqualTo(A.toString());
        assertThat(json.has("mapDifficultyId")).isFalse();
        assertThat(json.has("songName")).isFalse();
    }

    @Test
    void campaignDetailKeepsItsFlatShape() {
        PublicStaffUserResponse staff = PublicStaffUserResponse.builder().id(A).username("staff")
                .role(StaffRole.ADMIN).userId("1").avatarUrl("a").cdnAvatarUrl("c").active(true).build();
        CampaignBackgroundPlacement background = new CampaignBackgroundPlacement(100.0, 1.5, 2.0);
        List<CampaignTagResponse> tags = List.of(CampaignTagResponse.builder().id(A).name("tag").build());
        List<CampaignItemAwardResponse> items = List.of(CampaignItemAwardResponse.builder().itemId(A)
                .itemName("item").quantity(2).build());
        List<CampaignDifficultyResponse> difficulties = List.of(CampaignDifficultyResponse.builder().id(A).build());
        List<CampaignBarrierResponse> barriers = List.of(CampaignBarrierResponse.builder().id(B).build());
        List<CampaignTextResponse> texts = List.of(CampaignTextResponse.builder().id(C).content("hi").build());

        CampaignDetailResponse actual = CampaignDetailResponse.builder()
                .id(A).creatorId("1").creatorName("creator").creatorAlias("alias").name("name").slug("slug")
                .summary("summary").description("description").status(CampaignStatus.CURATED)
                .official(true).progressionAgnostic(true).completionMode(CampaignCompletionMode.TERMINAL)
                .legacy(true).completionXp(250.0).playlistExportEnabled(true).backgroundUrl("bg")
                .backgroundColor("#000").background(background).iconUrl("icon").difficultyCount(0)
                .totalUpvotes(3).totalDownvotes(1).voteScore(0.5).myVote(CampaignVoteDirection.UP)
                .tags(tags).completionItems(items).curatedAt(T1).curatedBy(staff).loved(true)
                .lovedAt(T2).lovedBy(staff).publishedAt(T1).createdAt(T2)
                .curatorNotes("notes").difficulties(difficulties).barriers(barriers).texts(texts).build();
        LegacyCampaignDetailResponse legacy = LegacyCampaignDetailResponse.builder()
                .id(A).creatorId("1").creatorName("creator").creatorAlias("alias").name("name").slug("slug")
                .summary("summary").description("description").status(CampaignStatus.CURATED).official(true)
                .progressionAgnostic(true).completionMode(CampaignCompletionMode.TERMINAL).legacy(true)
                .completionXp(250.0).curatorNotes("notes").playlistExportEnabled(true).backgroundUrl("bg")
                .backgroundColor("#000").background(background).iconUrl("icon").totalUpvotes(3).totalDownvotes(1)
                .voteScore(0.5).myVote(CampaignVoteDirection.UP).loved(true).lovedAt(T2).lovedBy(staff)
                .curatedAt(T1).curatedBy(staff).publishedAt(T1).createdAt(T2).tags(tags).difficulties(difficulties)
                .barriers(barriers).texts(texts).completionItems(items).build();

        assertSameJson(actual, legacy);
        assertThat(mapper.writeValueAsString(actual)).doesNotContain("difficultyCount");
    }

    @Test
    void campaignLeaderboardNestsThePlayer() {
        CampaignLeaderboardEntry entry = CampaignLeaderboardEntry.builder().rank(1).player(PLAYER).completedAt(T1)
                .averageAccuracy(0.98).averageAp(900.0).nodesCounted(4)
                .progressStatus(UserCampaignStatus.COMPLETED).completedNodes(4).totalNodes(5).build();

        assertNestedPlayer(json(entry));
    }

    @Test
    void aRetreatingWarCarriesWhenItEndsAndAnOpenOneLeavesItOut() {
        JsonNode retreating = json(new ClanWarResponse(A, null, null, null, ClanWarStatus.active, null, null, null,
                null, null, null, T1, T2, null));
        JsonNode open = json(new ClanWarResponse(A, null, null, null, ClanWarStatus.active, null, null, null,
                null, null, null, T1, null, null));

        assertThat(retreating.get("retreatAt").asString()).isEqualTo("2026-02-03T04:05:06Z");
        assertThat(open.has("retreatAt")).isFalse();
    }

    @Test
    void warParticipantNestsTheStrikePreviewWithPlainWholeNumbers() {
        ClanWarParticipantResponse row = new ClanWarParticipantResponse(PLAYER, null, null, 80.0, true,
                new ClanWarStrikePreviewResponse(30.0, 3, 0, 40.0, 80.0, 2.0,
                        new ClanWarStrikeMapResponse(null, false, false)), 1.0, 100.0, 0, 0,
                0.0, T1, null);

        JsonNode preview = json(row).get("strikePreview");

        assertNestedPlayer(json(row));
        assertThat(preview.get("damage").toString()).isEqualTo("30");
        assertThat(preview.get("hitsToBreak").asInt()).isEqualTo(3);
        assertThat(preview.get("disparity").toString()).isEqualTo("2");
        assertThat(preview.get("onMap").has("targetScore")).isFalse();
        assertThat(json(new ClanWarStrikeRulesResponse(100.0, 15.0, 1.5, 1.0, 0.5, 8.0, 0.3, 1.0)).get("guard")
                .toString()).isEqualTo("100");
    }

    @Test
    void campaignProgressSocketNestsThePlayerWithoutNulls() throws Exception {
        ObjectMapper socketMapper = PlainDoubleJackson2Module.mapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        PlayerRef noClan = new PlayerRef("76561198000000001", "Player", null, "cdn", "DE", null, null);
        var frame = socketMapper.readTree(socketMapper.writeValueAsString(
                new CampaignProgressBroadcast("node_completed", noClan, null, null, T1)));

        assertThat(frame.get("player").get("id").isTextual()).isTrue();
        assertThat(frame.get("player").get("id").asText()).isEqualTo("76561198000000001");
        assertThat(frame.get("player").get("name").asText()).isEqualTo("Player");
        assertThat(frame.get("player").has("clan")).isFalse();
        assertThat(frame.get("player").has("membership")).isFalse();
        assertThat(frame.has("userId")).isFalse();
    }

    @Test
    void milestoneSocketNestsThePlayerAndKeepsItsNulls() throws Exception {
        ObjectMapper socketMapper = PlainDoubleJackson2Module.mapper();
        var frame = socketMapper.readTree(socketMapper.writeValueAsString(MilestoneCompletedResponse.builder()
                .player(PLAYER).completedAt(T1).build()));

        assertThat(frame.get("player").get("id").isTextual()).isTrue();
        assertThat(frame.get("player").get("id").asText()).isEqualTo("76561198000000001");
        assertThat(frame.get("player").get("clan").get("tag").asText()).isEqualTo("TAG");
        assertThat(frame.get("player").get("membership").isNull()).isTrue();
        assertThat(frame.has("userId")).isFalse();
        assertThat(frame.has("userName")).isFalse();
        assertThat(frame.has("milestones")).isFalse();
    }

    @Test
    void statisticsBoardsNestThePlayer() {
        assertNestedPlayer(json(MissionCompletorResponse.builder().player(PLAYER).missionsCompleted(4)
                .missionXp(120.0).build()));
        assertNestedPlayer(json(EventMissionLeaderboardResponse.builder().rank(2).player(PLAYER).completions(5)
                .xpEarned(500).itemsAwarded(1).lastCompletedAt(T1).build()));
        assertThat(json(PLAYER).get("id").asString()).isEqualTo("76561198000000001");
        assertThat(json(PLAYER).has("membership")).isFalse();
    }

    @Test
    void statisticsBoardsNestTheMap() {
        JsonNode average = json(MapAvgApResponse.builder().map(MAP).averageWeightedAp(812.5).scoreCount(30)
                .latestScoreId(A).latestScoreTimeSet(T1).build());
        assertNestedMap(average);
        assertThat(average.get("averageWeightedAp").asDouble()).isEqualTo(812.5);

        JsonNode both = json(UserMapImprovementsResponse.builder().player(PLAYER).map(MAP).improvementCount(6)
                .latestScoreId(A).latestScoreTimeSet(T1).build());
        assertNestedPlayer(both);
        assertNestedMap(both);
        assertThat(both.get("map").get("difficulty").asString()).isEqualTo("EXPERT_PLUS");
    }

    @Test
    void milestoneHolderNestsThePlayer() {
        JsonNode holder = json(new MilestoneHolderResponse(PLAYER, T1));

        assertNestedPlayer(holder);
        assertThat(holder.get("completedAt").asString()).isEqualTo("2026-01-02T03:04:05Z");
    }

    @Test
    void discordLinkNestsThePlayer() {
        JsonNode link = json(DiscordLinkResponse.builder().discordId("123").player(PLAYER).createdAt(T1).build());

        assertNestedPlayer(link);
        assertThat(link.get("discordId").asString()).isEqualTo("123");
        assertThat(link.has("playerName")).isFalse();
    }

    @Test
    void milestoneCompletionNestsTheMap() {
        JsonNode completion = json(MilestoneCompletionResponse.builder().milestoneId(A).score(1000).maxScore(1100)
                .map(MAP).build());

        assertNestedMap(completion);
        assertThat(completion.has("coverUrl")).isFalse();
        assertThat(completion.get("maxScore").asInt()).isEqualTo(1100);
    }

    @Test
    void tradeNestsBothPlayersWithoutFlatIds() {
        JsonNode trade = json(TradeResponse.builder().fromUser(PLAYER).toUser(PLAYER).build());

        assertThat(trade.get("fromUser").get("id").isString()).isTrue();
        assertThat(trade.get("toUser").get("id").asString()).isEqualTo("76561198000000001");
        assertThat(trade.has("fromUserId")).isFalse();
        assertThat(trade.has("toUserId")).isFalse();
    }

    @Test
    void relationSendsUserIdsAsStrings() {
        var json = mapper.readTree(mapper.writeValueAsString(
                UserRelationResponse.builder().userId(76561198000000001L).targetUserId(76561198000000002L).build()));

        assertThat(json.get("userId").isString()).isTrue();
        assertThat(json.get("userId").asString()).isEqualTo("76561198000000001");
        assertThat(json.get("targetUserId").asString()).isEqualTo("76561198000000002");
    }

    @Test
    void publicBatchKeepsItsShape() {
        List<PublicMapDifficultyResponse> difficulties = List.of(PublicMapDifficultyResponse.builder().id(A)
                .songName("Song").difficulty(Difficulty.EXPERT_PLUS).complexity(7.0).build());
        BatchResponse<PublicMapDifficultyResponse> actual = BatchResponse.<PublicMapDifficultyResponse>builder()
                .id(A).name("name").description("description").status(BatchStatus.RELEASED)
                .difficulties(difficulties).releasedAt(T1).createdAt(T2).updatedAt(T1).build();
        LegacyPublicBatchResponse legacy = new LegacyPublicBatchResponse(A, "name", "description",
                BatchStatus.RELEASED, difficulties, T1, T2, T1);

        assertSameJson(actual, legacy);
    }

    @Getter
    @Builder
    static class LegacyCampaignDetailResponse {
        private UUID id;
        private String creatorId;
        private String creatorName;
        private String creatorAlias;
        private String name;
        private String slug;
        private String summary;
        private String description;
        private CampaignStatus status;
        private boolean official;
        private boolean progressionAgnostic;
        private CampaignCompletionMode completionMode;
        private boolean legacy;
        private Double completionXp;
        private String curatorNotes;
        private boolean playlistExportEnabled;
        private String backgroundUrl;
        private String backgroundColor;
        private CampaignBackgroundPlacement background;
        private String iconUrl;
        private int totalUpvotes;
        private int totalDownvotes;
        private double voteScore;
        private CampaignVoteDirection myVote;
        private boolean loved;
        private Instant lovedAt;
        private PublicStaffUserResponse lovedBy;
        private Instant curatedAt;
        private PublicStaffUserResponse curatedBy;
        private Instant publishedAt;
        private Instant createdAt;
        private List<CampaignTagResponse> tags;
        private List<CampaignDifficultyResponse> difficulties;
        private List<CampaignBarrierResponse> barriers;
        private List<CampaignTextResponse> texts;
        private List<CampaignItemAwardResponse> completionItems;
    }

    @Getter
    @AllArgsConstructor
    static class LegacyPublicBatchResponse {
        private final UUID id;
        private final String name;
        private final String description;
        private final BatchStatus status;
        private final List<PublicMapDifficultyResponse> difficulties;
        private final Instant releasedAt;
        private final Instant createdAt;
        private final Instant updatedAt;
    }
}
