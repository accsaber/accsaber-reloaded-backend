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
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTagResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignTextResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.dto.response.item.TradeResponse;
import com.accsaber.backend.model.dto.response.map.BatchResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneHolderResponse;
import com.accsaber.backend.model.dto.response.player.UserRelationResponse;
import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignCompletorResponse;
import com.accsaber.backend.model.dto.response.statistics.CampaignCreatorResponse;
import com.accsaber.backend.model.dto.response.statistics.EventMissionLeaderboardResponse;
import com.accsaber.backend.model.dto.response.statistics.MapAvgApResponse;
import com.accsaber.backend.model.dto.response.statistics.MapIdentity;
import com.accsaber.backend.model.dto.response.statistics.MapRetryResponse;
import com.accsaber.backend.model.dto.response.statistics.MissionCompletorResponse;
import com.accsaber.backend.model.dto.response.statistics.UserIdentity;
import com.accsaber.backend.model.dto.response.statistics.UserMapImprovementsResponse;
import com.accsaber.backend.model.entity.campaign.CampaignBackgroundPlacement;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.BatchStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import tools.jackson.databind.json.JsonMapper;

class ResponseJsonShapeTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final Instant T1 = Instant.parse("2026-01-02T03:04:05Z");
    private static final Instant T2 = Instant.parse("2026-02-03T04:05:06Z");
    private static final PublicClanResponse CLAN = new PublicClanResponse(C, "slug", "Clan", "TAG", "#112233",
            "#445566", "#778899", "icon", List.of());
    private static final UserIdentity USER = new UserIdentity("76561198000000001", "Player", "avatar", "cdnAvatar",
            CLAN, "DE");
    private static final MapIdentity MAP = new MapIdentity(A, B, "Song", "Sub", "Artist", "Mapper", "cover",
            "cdnCover", Difficulty.EXPERT_PLUS, C, "True Acc");

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
                .campaign(CampaignResponse.builder()
                        .id(A).creatorId("1").creatorName("creator").creatorAlias("alias").name("name").slug("slug")
                        .summary("summary").description("description").status(CampaignStatus.CURATED)
                        .official(true).progressionAgnostic(true).completionMode(CampaignCompletionMode.TERMINAL)
                        .legacy(true).completionXp(250.0).playlistExportEnabled(true).backgroundUrl("bg")
                        .backgroundColor("#000").background(background).iconUrl("icon").difficultyCount(0)
                        .totalUpvotes(3).totalDownvotes(1).voteScore(0.5).myVote(CampaignVoteDirection.UP)
                        .tags(tags).completionItems(items).curatedAt(T1).curatedBy(staff).loved(true)
                        .lovedAt(T2).lovedBy(staff).publishedAt(T1).createdAt(T2).build())
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
    void campaignLeaderboardPlayerIsTheUserIdentityShape() {
        CampaignLeaderboardEntry actual = CampaignLeaderboardEntry.builder().rank(1).player(USER).completedAt(T1)
                .averageAccuracy(0.98).averageAp(900.0).nodesCounted(4)
                .progressStatus(UserCampaignStatus.COMPLETED).completedNodes(4).totalNodes(5).build();
        LegacyCampaignLeaderboardEntry legacy = LegacyCampaignLeaderboardEntry.builder().rank(1)
                .player(legacyPlayer()).completedAt(T1).averageAccuracy(0.98).averageAp(900.0).nodesCounted(4)
                .progressStatus(UserCampaignStatus.COMPLETED).completedNodes(4).totalNodes(5).build();

        assertSameJson(actual, legacy);
    }

    @Test
    void campaignProgressSocketPlayerKeepsItsShape() throws Exception {
        ObjectMapper socketMapper = PlainDoubleJackson2Module.mapper()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        UserIdentity noClan = new UserIdentity("7", "Player", null, "cdn", null, "DE");
        LegacyCampaignLeaderboardPlayer legacy = LegacyCampaignLeaderboardPlayer.builder().userId("7")
                .userName("Player").cdnAvatarUrl("cdn").country("DE").build();

        assertThat(socketMapper.readTree(socketMapper.writeValueAsString(noClan)))
                .isEqualTo(socketMapper.readTree(socketMapper.writeValueAsString(legacy)));
    }

    @Test
    void userIdentityBlocksStayFlat() {
        assertSameJson(MissionCompletorResponse.builder().user(USER).missionsCompleted(4).missionXp(120.0).build(),
                LegacyMissionCompletorResponse.builder().userId(USER.userId()).userName(USER.userName())
                        .avatarUrl(USER.avatarUrl()).cdnAvatarUrl(USER.cdnAvatarUrl()).clan(CLAN)
                        .country(USER.country()).missionsCompleted(4).missionXp(120.0).build());
        assertSameJson(CampaignCompletorResponse.builder().user(USER).completed(2).inProgress(1).nodesCleared(9)
                .campaignXp(300.0).build(),
                LegacyCampaignCompletorResponse.builder().userId(USER.userId()).userName(USER.userName())
                        .avatarUrl(USER.avatarUrl()).cdnAvatarUrl(USER.cdnAvatarUrl()).clan(CLAN)
                        .country(USER.country()).completed(2).inProgress(1).nodesCleared(9).campaignXp(300.0)
                        .build());
        assertSameJson(CampaignCreatorResponse.builder().user(USER).campaigns(3).curatedCampaigns(1)
                .participants(40).completions(10).completionRate(0.25).build(),
                LegacyCampaignCreatorResponse.builder().userId(USER.userId()).userName(USER.userName())
                        .avatarUrl(USER.avatarUrl()).cdnAvatarUrl(USER.cdnAvatarUrl()).clan(CLAN)
                        .country(USER.country()).campaigns(3).curatedCampaigns(1).participants(40).completions(10)
                        .completionRate(0.25).build());
        assertSameJson(EventMissionLeaderboardResponse.builder().rank(2).user(USER).completions(5).xpEarned(500)
                .itemsAwarded(1).lastCompletedAt(T1).build(),
                LegacyEventMissionLeaderboardResponse.builder().rank(2).userId(USER.userId())
                        .userName(USER.userName()).avatarUrl(USER.avatarUrl()).cdnAvatarUrl(USER.cdnAvatarUrl())
                        .clan(CLAN).country(USER.country()).completions(5).xpEarned(500).itemsAwarded(1)
                        .lastCompletedAt(T1).build());
    }

    @Test
    void mapIdentityBlocksStayFlat() {
        assertSameJson(MapAvgApResponse.builder().map(MAP).averageWeightedAp(812.5).scoreCount(30)
                .latestScoreId(A).latestScoreTimeSet(T1).build(),
                LegacyMapAvgApResponse.builder().mapDifficultyId(A).mapId(B).songName("Song").songSubName("Sub")
                        .songAuthor("Artist").mapAuthor("Mapper").coverUrl("cover").cdnCoverUrl("cdnCover")
                        .difficulty(Difficulty.EXPERT_PLUS).categoryId(C).categoryName("True Acc")
                        .averageWeightedAp(812.5).scoreCount(30).latestScoreId(A).latestScoreTimeSet(T1).build());
        assertSameJson(MapRetryResponse.builder().map(MAP).supersededCount(12).latestScoreId(A)
                .latestScoreTimeSet(T1).build(),
                LegacyMapRetryResponse.builder().mapDifficultyId(A).mapId(B).songName("Song").songSubName("Sub")
                        .songAuthor("Artist").mapAuthor("Mapper").coverUrl("cover").cdnCoverUrl("cdnCover")
                        .difficulty(Difficulty.EXPERT_PLUS).categoryId(C).categoryName("True Acc")
                        .supersededCount(12).latestScoreId(A).latestScoreTimeSet(T1).build());
        assertSameJson(UserMapImprovementsResponse.builder().user(USER).map(MAP).improvementCount(6)
                .latestScoreId(A).latestScoreTimeSet(T1).build(),
                LegacyUserMapImprovementsResponse.builder().userId(USER.userId()).userName(USER.userName())
                        .avatarUrl(USER.avatarUrl()).cdnAvatarUrl(USER.cdnAvatarUrl()).clan(CLAN)
                        .country(USER.country()).mapDifficultyId(A).mapId(B).songName("Song").songSubName("Sub")
                        .songAuthor("Artist").mapAuthor("Mapper").coverUrl("cover").cdnCoverUrl("cdnCover")
                        .difficulty(Difficulty.EXPERT_PLUS).categoryId(C).categoryName("True Acc")
                        .improvementCount(6).latestScoreId(A).latestScoreTimeSet(T1).build());
    }

    @Test
    void milestoneHolderSendsTheUserIdAsAString() {
        MilestoneHolderResponse holder = new MilestoneHolderResponse(7L, "Player", "a", "c", "DE", T1)
                .withClan(CLAN);

        assertSameJson(holder, new LegacyMilestoneHolderResponse("7", "Player", "a", "c", CLAN, "DE", T1));
    }

    @Test
    void tradeSendsUserIdsAsStrings() {
        var json = mapper.readTree(mapper.writeValueAsString(
                TradeResponse.builder().fromUserId(76561198000000001L).toUserId(76561198000000002L).build()));

        assertThat(json.get("fromUserId").isString()).isTrue();
        assertThat(json.get("fromUserId").asString()).isEqualTo("76561198000000001");
        assertThat(json.get("toUserId").asString()).isEqualTo("76561198000000002");
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

    private static LegacyCampaignLeaderboardPlayer legacyPlayer() {
        return LegacyCampaignLeaderboardPlayer.builder().userId(USER.userId()).userName(USER.userName())
                .country(USER.country()).avatarUrl(USER.avatarUrl()).cdnAvatarUrl(USER.cdnAvatarUrl()).clan(CLAN)
                .build();
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

    @Getter
    @AllArgsConstructor
    static class LegacyMilestoneHolderResponse {
        private final String userId;
        private final String name;
        private final String avatarUrl;
        private final String cdnAvatarUrl;
        private final PublicClanResponse clan;
        private final String country;
        private final Instant completedAt;
    }

    @Getter
    @Builder
    static class LegacyCampaignLeaderboardPlayer {
        private String userId;
        private String userName;
        private String country;
        private String avatarUrl;
        private String cdnAvatarUrl;
        private PublicClanResponse clan;
    }

    @Getter
    @Builder
    static class LegacyCampaignLeaderboardEntry {
        private Integer rank;
        private LegacyCampaignLeaderboardPlayer player;
        private Instant completedAt;
        private Double averageAccuracy;
        private Double averageAp;
        private Integer nodesCounted;
        private UserCampaignStatus progressStatus;
        private Integer completedNodes;
        private Integer totalNodes;
    }

    @Getter
    @Builder
    static class LegacyMissionCompletorResponse {
        private String userId;
        private String userName;
        private String avatarUrl;
        private String cdnAvatarUrl;
        private PublicClanResponse clan;
        private String country;
        private long missionsCompleted;
        private double missionXp;
    }

    @Getter
    @Builder
    static class LegacyCampaignCompletorResponse {
        private String userId;
        private String userName;
        private String avatarUrl;
        private String cdnAvatarUrl;
        private PublicClanResponse clan;
        private String country;
        private long completed;
        private long inProgress;
        private long nodesCleared;
        private double campaignXp;
    }

    @Getter
    @Builder
    static class LegacyCampaignCreatorResponse {
        private String userId;
        private String userName;
        private String avatarUrl;
        private String cdnAvatarUrl;
        private PublicClanResponse clan;
        private String country;
        private long campaigns;
        private long curatedCampaigns;
        private long participants;
        private long completions;
        private Double completionRate;
    }

    @Getter
    @Builder
    static class LegacyEventMissionLeaderboardResponse {
        private long rank;
        private String userId;
        private String userName;
        private String avatarUrl;
        private String cdnAvatarUrl;
        private PublicClanResponse clan;
        private String country;
        private long completions;
        private long xpEarned;
        private long itemsAwarded;
        private Instant lastCompletedAt;
    }

    @Getter
    @Builder
    static class LegacyMapAvgApResponse {
        private UUID mapDifficultyId;
        private UUID mapId;
        private String songName;
        private String songSubName;
        private String songAuthor;
        private String mapAuthor;
        private String coverUrl;
        private String cdnCoverUrl;
        private Difficulty difficulty;
        private UUID categoryId;
        private String categoryName;
        private Double averageWeightedAp;
        private long scoreCount;
        private UUID latestScoreId;
        private Instant latestScoreTimeSet;
    }

    @Getter
    @Builder
    static class LegacyMapRetryResponse {
        private UUID mapDifficultyId;
        private UUID mapId;
        private String songName;
        private String songSubName;
        private String songAuthor;
        private String mapAuthor;
        private String coverUrl;
        private String cdnCoverUrl;
        private Difficulty difficulty;
        private UUID categoryId;
        private String categoryName;
        private long supersededCount;
        private UUID latestScoreId;
        private Instant latestScoreTimeSet;
    }

    @Getter
    @Builder
    static class LegacyUserMapImprovementsResponse {
        private String userId;
        private String userName;
        private String avatarUrl;
        private String cdnAvatarUrl;
        private PublicClanResponse clan;
        private String country;
        private UUID mapDifficultyId;
        private UUID mapId;
        private String songName;
        private String songSubName;
        private String songAuthor;
        private String mapAuthor;
        private String coverUrl;
        private String cdnCoverUrl;
        private Difficulty difficulty;
        private UUID categoryId;
        private String categoryName;
        private long improvementCount;
        private UUID latestScoreId;
        private Instant latestScoreTimeSet;
    }
}
