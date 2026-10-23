package com.accsaber.backend.model.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.accsaber.backend.config.PlainDoubleJacksonConfig;
import com.accsaber.backend.model.dto.request.campaign.CampaignModifierRequirementRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignVoteRequest;
import com.accsaber.backend.model.dto.request.campaign.InviteCampaignCollaboratorRequest;
import com.accsaber.backend.model.dto.request.clan.ChooseClanWarTargetRequest;
import com.accsaber.backend.model.entity.clan.war.ClanWarTargeting;
import com.accsaber.backend.model.dto.request.clan.ClanCapacityRequest;
import com.accsaber.backend.model.dto.request.clan.ClanWarModeRequest;
import com.accsaber.backend.model.dto.request.clan.CreateClanAllianceRequest;
import com.accsaber.backend.model.dto.request.clan.CreateClanJoinRequest;
import com.accsaber.backend.model.dto.request.clan.CreateClanRivalRequest;
import com.accsaber.backend.model.dto.request.clan.EquipClanItemRequest;
import com.accsaber.backend.model.dto.request.clan.OfferClanWarAidRequest;
import com.accsaber.backend.model.dto.request.clan.RequestClanWarAidRequest;
import com.accsaber.backend.model.dto.request.clan.ResolveClanAllianceRequest;
import com.accsaber.backend.model.dto.request.clan.ResolveClanJoinRequest;
import com.accsaber.backend.model.dto.request.clan.ResolveClanWarAidRequest;
import com.accsaber.backend.model.dto.request.clan.TransferClanFounderRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanMemberRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanPacifismRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanWarRequest;
import com.accsaber.backend.model.dto.request.clan.UpdateClanWarSideRequest;
import com.accsaber.backend.model.dto.request.discord.UpdateDiscordLinkRequest;
import com.accsaber.backend.model.dto.request.item.EquipItemRequest;
import com.accsaber.backend.model.dto.request.item.UpdateItemModifierRequest;
import com.accsaber.backend.model.dto.request.item.UpdateItemTypeRequest;
import com.accsaber.backend.model.dto.request.item.UpdateUnusualEffectRequest;
import com.accsaber.backend.model.dto.request.item.UpsertCrateContentRequest;
import com.accsaber.backend.model.dto.request.item.UpsertCrateModifierRequest;
import com.accsaber.backend.model.dto.request.map.ApproveReweightRequest;
import com.accsaber.backend.model.dto.request.map.ApproveUnrankRequest;
import com.accsaber.backend.model.dto.request.map.BatchReweightRequest;
import com.accsaber.backend.model.dto.request.map.BulkUnrankRequest;
import com.accsaber.backend.model.dto.request.map.UpdateMapCategoryRequest;
import com.accsaber.backend.model.dto.request.market.PlaceBidRequest;
import com.accsaber.backend.model.dto.request.milestone.ActivateMilestonesRequest;
import com.accsaber.backend.model.dto.request.milestone.AddMapDifficultyLinksRequest;
import com.accsaber.backend.model.dto.request.notification.BroadcastRequest;
import com.accsaber.backend.model.dto.request.staff.ForceChangePasswordRequest;
import com.accsaber.backend.model.dto.request.staff.LinkUserRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffRoleRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffStatusRequest;
import com.accsaber.backend.model.dto.request.supporter.AssignSupporterEventRequest;
import com.accsaber.backend.model.dto.request.supporter.ClaimByRoleSignalRequest;
import com.accsaber.backend.model.dto.request.supporter.ClaimSupporterEventRequest;
import com.accsaber.backend.model.dto.request.user.CountryOverrideRequest;
import com.accsaber.backend.model.dto.request.user.MergeUsersRequest;
import com.accsaber.backend.model.entity.campaign.CampaignModifierRequirement;
import com.accsaber.backend.model.entity.campaign.CampaignVoteDirection;
import com.accsaber.backend.model.entity.clan.ClanAllianceStatus;
import com.accsaber.backend.model.entity.clan.ClanJoinStatus;
import com.accsaber.backend.model.entity.clan.ClanRole;
import com.accsaber.backend.model.entity.clan.war.ClanWarAidStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarStatus;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.json.JsonMapper;

class RequestRecordDeserializationTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

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

    static Stream<Arguments> bodies() {
        ApproveReweightRequest reweight = new ApproveReweightRequest();
        reweight.setMapDifficultyId(A);
        reweight.setComplexity(7.5);
        reweight.setReason("r");
        ApproveUnrankRequest unrank = new ApproveUnrankRequest();
        unrank.setMapDifficultyId(A);
        unrank.setReason("r");
        return Stream.of(
                Arguments.of("{\"milestoneIds\":[\"" + A + "\"]}", new ActivateMilestonesRequest(List.of(A))),
                Arguments.of("{\"mapDifficultyIds\":[\"" + A + "\",\"" + B + "\"]}",
                        new AddMapDifficultyLinksRequest(List.of(A, B))),
                Arguments.of("{\"kofiTransactionId\":\"k\",\"discordId\":\"d\"}",
                        new AssignSupporterEventRequest("k", "d")),
                Arguments.of("{\"items\":[{\"mapDifficultyId\":\"" + A + "\",\"complexity\":7.5,\"reason\":\"r\"}]}",
                        new BatchReweightRequest(List.of(reweight))),
                Arguments.of("{\"title\":\"t\",\"linkTo\":\"/x\"}", new BroadcastRequest("t", "/x")),
                Arguments.of("{\"items\":[{\"mapDifficultyId\":\"" + A + "\",\"reason\":\"r\"}]}",
                        new BulkUnrankRequest(List.of(unrank))),
                Arguments.of("{\"modifierId\":\"" + A + "\",\"requirement\":\"REQUIRED\"}",
                        new CampaignModifierRequirementRequest(A, CampaignModifierRequirement.REQUIRED)),
                Arguments.of("{\"direction\":\"UP\"}", new CampaignVoteRequest(CampaignVoteDirection.UP)),
                Arguments.of("{\"targeting\":\"manual\",\"userId\":\"76561198000000000\"}",
                        new ChooseClanWarTargetRequest(ClanWarTargeting.manual, "76561198000000000")),
                Arguments.of("{\"discordId\":\"d\",\"tierName\":\"Gold\",\"assignedAt\":\"2026-01-02T03:04:05Z\"}",
                        new ClaimByRoleSignalRequest("d", "Gold", Instant.parse("2026-01-02T03:04:05Z"))),
                Arguments.of("{\"kofiTransactionId\":\"k\",\"userId\":42}", new ClaimSupporterEventRequest("k", 42L)),
                Arguments.of("{\"amount\":3}", new ClanCapacityRequest(3)),
                Arguments.of("{\"level\":2}", new ClanWarModeRequest(2)),
                Arguments.of("{\"country\":\"DE\"}", new CountryOverrideRequest("DE")),
                Arguments.of("{\"clanId\":\"" + A + "\"}", new CreateClanAllianceRequest(A)),
                Arguments.of("{\"userId\":\"7\"}", new CreateClanJoinRequest("7")),
                Arguments.of("{\"clanId\":\"" + A + "\"}", new CreateClanRivalRequest(A)),
                Arguments.of("{\"itemId\":\"" + A + "\",\"variantKey\":\"v\"}", new EquipClanItemRequest(A, "v")),
                Arguments.of("{\"linkId\":\"" + A + "\",\"variantKey\":\"v\"}", new EquipItemRequest(A, "v")),
                Arguments.of("{\"newPassword\":\"pw\"}", new ForceChangePasswordRequest("pw")),
                Arguments.of("{\"userId\":42}", new InviteCampaignCollaboratorRequest(42L)),
                Arguments.of("{\"userId\":42}", new LinkUserRequest(42L)),
                Arguments.of("{\"primaryUserId\":1,\"secondaryUserId\":2,\"reason\":\"dup\"}",
                        new MergeUsersRequest(1L, 2L, "dup")),
                Arguments.of("{\"team\":\"attack\"}", new OfferClanWarAidRequest(ClanWarTeam.attack)),
                Arguments.of("{\"amount\":500}", new PlaceBidRequest(500L)),
                Arguments.of("{\"clanId\":\"" + A + "\"}", new RequestClanWarAidRequest(A)),
                Arguments.of("{\"status\":\"active\"}", new ResolveClanAllianceRequest(ClanAllianceStatus.active)),
                Arguments.of("{\"status\":\"accepted\"}", new ResolveClanJoinRequest(ClanJoinStatus.accepted)),
                Arguments.of("{\"status\":\"declined\"}", new ResolveClanWarAidRequest(ClanWarAidStatus.declined)),
                Arguments.of("{\"userId\":\"7\"}", new TransferClanFounderRequest("7")),
                Arguments.of("{\"role\":\"officer\"}", new UpdateClanMemberRequest(ClanRole.officer)),
                Arguments.of("{\"enabled\":true}", new UpdateClanPacifismRequest(true)),
                Arguments.of("{\"status\":\"ended\"}", new UpdateClanWarRequest(ClanWarStatus.ended)),
                Arguments.of("{\"left\":true}", new UpdateClanWarSideRequest(true)),
                Arguments.of("{\"profileUrl\":\"https://beatleader.com/u/1\"}",
                        new UpdateDiscordLinkRequest("https://beatleader.com/u/1")),
                Arguments.of("{\"globalDropChance\":0.25,\"seasonStart\":\"12-01\",\"seasonEnd\":\"01-01\"}",
                        new UpdateItemModifierRequest(0.25, "12-01", "01-01")),
                Arguments.of("{\"name\":\"n\",\"description\":\"d\",\"valueSchema\":{\"k\":1}}",
                        new UpdateItemTypeRequest("n", "d", Map.of("k", 1))),
                Arguments.of("{\"categoryId\":\"" + A + "\"}", new UpdateMapCategoryRequest(A)),
                Arguments.of("{\"role\":\"RANKING_HEAD\"}", new UpdateStaffRoleRequest(StaffRole.RANKING_HEAD)),
                Arguments.of("{\"status\":\"ACCEPTED\"}", new UpdateStaffStatusRequest(StaffUserStatus.ACCEPTED)),
                Arguments.of("{\"name\":\"n\",\"description\":\"d\",\"effectSpec\":{\"k\":\"v\"}}",
                        new UpdateUnusualEffectRequest("n", "d", Map.of("k", "v"))),
                Arguments.of("{\"dropWeight\":5}", new UpsertCrateContentRequest(5)),
                Arguments.of("{\"dropChance\":0.5}", new UpsertCrateModifierRequest(0.5)));
    }

    @ParameterizedTest
    @MethodSource("bodies")
    void deserializesObjectBody(String json, Record expected) {
        Object actual = mapper.readValue(json, expected.getClass());
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void constraintsOnComponentsStillValidate() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(new UpdateStaffRoleRequest(null)))
                    .extracting(v -> v.getPropertyPath().toString()).containsExactly("role");
            assertThat(validator.validate(new PlaceBidRequest(0L)))
                    .extracting(v -> v.getPropertyPath().toString()).containsExactly("amount");
            assertThat(validator.validate(new CountryOverrideRequest("deu"))).hasSize(1);
            assertThat(validator.validate(new UpdateClanWarSideRequest(false))).hasSize(1);
            assertThat(validator.validate(new MergeUsersRequest(1L, 2L, null))).isEmpty();
        }
    }
}
