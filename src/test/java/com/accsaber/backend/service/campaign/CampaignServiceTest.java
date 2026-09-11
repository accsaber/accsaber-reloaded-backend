package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CampaignBound;
import com.accsaber.backend.model.dto.request.campaign.CampaignFilter;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.SetCampaignItemRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignItemAwardResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignBackgroundPlacement;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionItem;
import com.accsaber.backend.model.entity.campaign.CampaignCompletionMode;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignDifficultyItem;
import com.accsaber.backend.model.entity.campaign.CampaignNodeBorderLayer;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignRewardTotals;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.CampaignTag;
import com.accsaber.backend.model.entity.campaign.CampaignTagKind;
import com.accsaber.backend.model.entity.campaign.CampaignTagLink;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.ModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyModifierRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyTargetRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.CampaignRewardTotalsRepository;
import com.accsaber.backend.repository.campaign.CampaignTagLinkRepository;
import com.accsaber.backend.repository.campaign.CampaignTagRepository;
import com.accsaber.backend.repository.campaign.CampaignTextRepository;
import com.accsaber.backend.repository.campaign.CampaignVoteRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.RichTextSanitizer;
import com.accsaber.backend.service.playlist.PlaylistService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

        @Mock
        private CampaignRepository campaignRepository;
        @Mock
        private CampaignCollaboratorRepository campaignCollaboratorRepository;
        @Mock
        private CampaignDifficultyRepository campaignDifficultyRepository;
        @Mock
        private CampaignDifficultyPathRepository campaignDifficultyPathRepository;
        @Mock
        private CampaignBarrierAffectedDifficultyRepository barrierAffectedRepository;
        @Mock
        private CampaignTextRepository campaignTextRepository;
        @Mock
        private RichTextSanitizer richTextSanitizer;
        @Mock
        private CampaignDifficultyItemRepository campaignDifficultyItemRepository;
        @Mock
        private CampaignCompletionItemRepository campaignCompletionItemRepository;
        @Mock
        private ItemRepository itemRepository;
        @Mock
        private CampaignTagRepository campaignTagRepository;
        @Mock
        private CampaignTagLinkRepository campaignTagLinkRepository;
        @Mock
        private UserCampaignRepository userCampaignRepository;
        @Mock
        private UserCampaignScoreRepository userCampaignScoreRepository;
        @Mock
        private CampaignVoteRepository campaignVoteRepository;
        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private ScoreModifierLinkRepository scoreModifierLinkRepository;
        @Mock
        private CampaignDifficultyModifierRepository campaignDifficultyModifierRepository;
        @Mock
        private CampaignDifficultyTargetRepository campaignDifficultyTargetRepository;
        @Mock
        private CampaignRewardTotalsRepository campaignRewardTotalsRepository;
        @Mock
        private ModifierRepository modifierRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private MapDifficultyComplexityRepository mapDifficultyComplexityRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private StaffUserRepository staffUserRepository;
        @Mock
        private DuplicateUserService duplicateUserService;
        @Mock
        private CampaignEvaluationService campaignEvaluationService;
        @Mock
        private com.accsaber.backend.service.score.CampaignScoreGate campaignScoreGate;
        @Mock
        private com.accsaber.backend.service.map.MapImportService mapImportService;
        @Mock
        private PlaylistService playlistService;
        @Mock
        private CdnProperties cdnProperties;

        @InjectMocks
        private CampaignService campaignService;

        private User creator;
        private Campaign campaign;
        private com.accsaber.backend.model.entity.map.Map map;
        private MapDifficulty mapDifficulty;

        @BeforeEach
        void setUp() {
                lenient().when(duplicateUserService.resolvePrimaryUserId(any(Long.class)))
                                .thenAnswer(inv -> inv.getArgument(0));
                creator = User.builder().id(12345L).name("TestPlayer").active(true).build();
                map = com.accsaber.backend.model.entity.map.Map.builder()
                                .id(UUID.randomUUID())
                                .songName("Test Song")
                                .songAuthor("Test Author")
                                .mapAuthor("Test Mapper")
                                .coverUrl("https://example.com/cover.png")
                                .songHash("abc123")
                                .build();
                mapDifficulty = MapDifficulty.builder()
                                .id(UUID.randomUUID())
                                .map(map)
                                .difficulty(Difficulty.EXPERT_PLUS)
                                .characteristic("Standard")
                                .maxScore(1000000)
                                .build();
                campaign = Campaign.builder()
                                .id(UUID.randomUUID())
                                .creator(creator)
                                .name("Test Campaign")
                                .slug("test-campaign")
                                .status(CampaignStatus.DRAFT)
                                .completionXp(0.0)
                                .active(true)
                                .campaignDifficulties(List.of())
                                .build();
        }

        @Nested
        class CreateCampaign {

                @Test
                void createsDraftCampaign() {
                        CreateCampaignRequest request = new CreateCampaignRequest();
                        request.setCreatorId(creator.getId());
                        request.setName("New Campaign");

                        when(userRepository.findByIdAndActiveTrue(creator.getId())).thenReturn(Optional.of(creator));
                        when(campaignRepository.existsBySlug(anyString())).thenReturn(false);
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
                                Campaign c = inv.getArgument(0);
                                c.setId(UUID.randomUUID());
                                c.setCampaignDifficulties(List.of());
                                return c;
                        });
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.createCampaign(request);

                        assertThat(result.getName()).isEqualTo("New Campaign");
                        assertThat(result.getSlug()).isEqualTo("new-campaign");
                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.DRAFT);
                }

                @Test
                void rejectsTakenSlug() {
                        CreateCampaignRequest request = new CreateCampaignRequest();
                        request.setCreatorId(creator.getId());
                        request.setName("Taken");
                        request.setSlug("taken");

                        when(userRepository.findByIdAndActiveTrue(creator.getId())).thenReturn(Optional.of(creator));
                        when(campaignRepository.existsBySlug("taken")).thenReturn(true);

                        assertThatThrownBy(() -> campaignService.createCampaign(request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void requiresCreatorIdOrAlias() {
                        CreateCampaignRequest request = new CreateCampaignRequest();
                        request.setName("Anon");

                        assertThatThrownBy(() -> campaignService.createCampaign(request))
                                        .isInstanceOf(ValidationException.class);
                }
        }

        @Nested
        class UpdateCampaign {

                @Test
                void updatesNameInDraft() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Renamed");

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getName()).isEqualTo("Renamed");
                }

                @Test
                void allowsUpdateWhenPublished() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Renamed");

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getName()).isEqualTo("Renamed");
                }

                @Test
                void allowsAdminUpdateWhenCurated() {
                        campaign.setStatus(CampaignStatus.CURATED);
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Renamed");
                        stubUpdate();

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getName()).isEqualTo("Renamed");
                }

                @Test
                void allowsAdminTagChangeWhenCurated() {
                        campaign.setStatus(CampaignStatus.CURATED);
                        CampaignTag tag = CampaignTag.builder()
                                        .id(UUID.randomUUID())
                                        .kind(CampaignTagKind.THEME)
                                        .name("Jumps")
                                        .build();
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setTagIds(List.of(tag.getId()));
                        stubUpdate();
                        when(campaignTagRepository.findByIdAndActiveTrue(tag.getId())).thenReturn(Optional.of(tag));
                        when(campaignTagLinkRepository.save(any(CampaignTagLink.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        campaignService.updateCampaign(campaign.getId(), request);

                        verify(campaignTagLinkRepository).save(any(CampaignTagLink.class));
                }

                @Test
                void storesBackgroundPlacementWhenAllThreeSupplied() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setBackground(placement("140", "50", "20"));
                        stubUpdate();

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getBackground())
                                        .isEqualTo(placement("140", "50", "20"));
                }

                @Test
                void keepsDecimalBackgroundPlacement() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setBackground(placement("140.5", "2.5", "-3.25"));
                        stubUpdate();

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getBackground().getSize()).isEqualByComparingTo(140.5);
                        assertThat(result.getBackground().getX()).isEqualByComparingTo(2.5);
                        assertThat(result.getBackground().getY()).isEqualByComparingTo(-3.25);
                }

                @Test
                void emptyBackgroundObjectClearsPlacement() {
                        campaign.setBackground(placement("140", "50", "20"));
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setBackground(new CampaignBackgroundPlacement());
                        stubUpdate();

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getBackground()).isNull();
                        assertThat(campaign.getBackground()).isNull();
                }

                @Test
                void omittedBackgroundLeavesPlacementUntouched() {
                        campaign.setBackground(placement("140", "50", "20"));
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Renamed");
                        stubUpdate();

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getBackground())
                                        .isEqualTo(placement("140", "50", "20"));
                }

                @Test
                void rejectsPartialBackgroundPlacement() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setBackground(new CampaignBackgroundPlacement(140.0, null, null));
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.updateCampaign(campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsOutOfRangeBackgroundSize() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setBackground(placement("0", "50", "20"));
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.updateCampaign(campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void removingBackgroundImageClearsPlacement() {
                        campaign.setBackgroundUrl("https://cdn.example/bg.png");
                        campaign.setBackground(placement("140", "50", "20"));
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.setBackgroundUrl(campaign.getId(), null);

                        assertThat(result.getBackgroundUrl()).isNull();
                        assertThat(result.getBackground()).isNull();
                }

                private void stubUpdate() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());
                }

                private static CampaignBackgroundPlacement placement(String size, String x, String y) {
                        return new CampaignBackgroundPlacement(Double.valueOf(size), Double.valueOf(x),
                                        Double.valueOf(y));
                }
        }

        @Nested
        class RewardTotals {

                private final UUID crateId = UUID.randomUUID();
                private final UUID titleId = UUID.randomUUID();

                @Test
                void sumsAnItemAwardedByBothANodeAndCampaignCompletion() {
                        stubCampaignPage();
                        when(campaignDifficultyItemRepository.findActiveByCampaignIds(List.of(campaign.getId())))
                                        .thenReturn(List.of(nodeAward(crateId, "Alpha Crate", 4),
                                                        nodeAward(titleId, "Founder Title", 1)));
                        when(campaignCompletionItemRepository.findByCampaign_IdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of(completionAward(crateId, "Alpha Crate", 2)));

                        CampaignResponse result = firstListedCampaign();

                        assertThat(result.getRewards())
                                        .extracting(CampaignItemAwardResponse::getItemName,
                                                        CampaignItemAwardResponse::getQuantity)
                                        .containsExactlyInAnyOrder(
                                                        org.assertj.core.api.Assertions.tuple("Alpha Crate", 6),
                                                        org.assertj.core.api.Assertions.tuple("Founder Title", 1));
                }

                @Test
                void reportsTotalsFromTheRewardTotalsView() {
                        stubCampaignPage();
                        when(campaignDifficultyItemRepository.findActiveByCampaignIds(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignCompletionItemRepository.findByCampaign_IdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignRewardTotalsRepository.findByCampaignIdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of(new CampaignRewardTotals(campaign.getId(),
                                                        4500, 9)));

                        CampaignResponse result = firstListedCampaign();

                        assertThat(result.getTotalXp()).isEqualByComparingTo(4500.0);
                        assertThat(result.getTotalRewardCount()).isEqualTo(9);
                }

                @Test
                void leavesTotalsNullWhenTheViewHasNoRow() {
                        stubCampaignPage();
                        when(campaignDifficultyItemRepository.findActiveByCampaignIds(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignCompletionItemRepository.findByCampaign_IdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignRewardTotalsRepository.findByCampaignIdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());

                        CampaignResponse result = firstListedCampaign();

                        assertThat(result.getTotalXp()).isNull();
                        assertThat(result.getTotalRewardCount()).isNull();
                        assertThat(result.getRewards()).isEmpty();
                }

                private CampaignResponse firstListedCampaign() {
                        Page<CampaignResponse> page = campaignService.findCampaigns(
                                        CampaignFilter.builder().build(), null, true, PageRequest.of(0, 20));
                        assertThat(page.getContent()).hasSize(1);
                        return page.getContent().get(0);
                }

                private void stubCampaignPage() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        when(campaignRepository.findFiltered(anyBoolean(), any(), any(), anyBoolean(), any(), any(),
                                        any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any()))
                                        .thenReturn(new PageImpl<>(List.of(campaign)));
                        when(campaignTagLinkRepository.findByCampaign_IdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignDifficultyRepository.countActiveByCampaignIds(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        lenient().when(campaignRewardTotalsRepository.findByCampaignIdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                }

                private CampaignDifficultyItem nodeAward(UUID itemId, String name, int quantity) {
                        CampaignDifficulty node = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).active(true).build();
                        return CampaignDifficultyItem.builder()
                                        .campaignDifficulty(node)
                                        .item(Item.builder().id(itemId).name(name).build())
                                        .quantity(quantity)
                                        .build();
                }

                private CampaignCompletionItem completionAward(UUID itemId, String name, int quantity) {
                        return CampaignCompletionItem.builder()
                                        .campaign(campaign)
                                        .item(Item.builder().id(itemId).name(name).build())
                                        .quantity(quantity)
                                        .build();
                }
        }

        @Nested
        class Loved {

                private final StaffUser curator = StaffUser.builder()
                                .id(UUID.randomUUID()).username("curator").role(StaffRole.CAMPAIGN_CURATOR).build();

                @Test
                void stampsTheCuratorAndTimestampWhenLoved() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        stubLovedUpdate();

                        CampaignResponse result = campaignService.setLoved(campaign.getId(), true, curator.getId());

                        assertThat(result.isLoved()).isTrue();
                        assertThat(result.getLovedAt()).isNotNull();
                        assertThat(result.getLovedBy()).isNotNull();
                        assertThat(result.getLovedBy().getId()).isEqualTo(curator.getId());
                        assertThat(result.getLovedBy().getUsername()).isEqualTo("curator");
                        assertThat(result.getLovedBy().getRole()).isEqualTo(StaffRole.CAMPAIGN_CURATOR);
                }

                @Test
                void clearsTheStampWhenUnloved() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        campaign.setLoved(true);
                        campaign.setLovedAt(Instant.now());
                        campaign.setLovedBy(curator);
                        stubLovedUpdate();

                        CampaignResponse result = campaignService.setLoved(campaign.getId(), false, curator.getId());

                        assertThat(result.isLoved()).isFalse();
                        assertThat(result.getLovedAt()).isNull();
                        assertThat(result.getLovedBy()).isNull();
                }

                @Test
                void allowsLovingAnAlreadyCuratedCampaign() {
                        campaign.setStatus(CampaignStatus.CURATED);
                        stubLovedUpdate();

                        CampaignResponse result = campaignService.setLoved(campaign.getId(), true, curator.getId());

                        assertThat(result.isLoved()).isTrue();
                }

                @Test
                void rejectsLovingADraft() {
                        campaign.setStatus(CampaignStatus.DRAFT);
                        stubCurator();
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.setLoved(campaign.getId(), true, curator.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsANonCuratorStaffMember() {
                        StaffUser ranking = StaffUser.builder()
                                        .id(UUID.randomUUID()).role(StaffRole.RANKING).build();
                        when(staffUserRepository.findByIdAndActiveTrue(ranking.getId()))
                                        .thenReturn(Optional.of(ranking));

                        assertThatThrownBy(() -> campaignService.setLoved(campaign.getId(), true, ranking.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsAnUnknownStaffId() {
                        UUID unknown = UUID.randomUUID();
                        when(staffUserRepository.findByIdAndActiveTrue(unknown)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> campaignService.setLoved(campaign.getId(), true, unknown))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void sortsNeverLovedCampaignsBehindLovedOnes() {
                        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
                        when(campaignRepository.findFiltered(anyBoolean(), any(), any(), anyBoolean(), any(), any(),
                                        any(), anyBoolean(), any(), any(), any(), any(), any(), any(),
                                        pageable.capture()))
                                        .thenReturn(new PageImpl<>(List.of()));

                        campaignService.findCampaigns(CampaignFilter.builder().build(), null, false,
                                        PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "lovedAt")));

                        assertThat(pageable.getValue().getSort())
                                        .extracting(Sort.Order::getProperty, Sort.Order::getDirection,
                                                        Sort.Order::getNullHandling)
                                        .containsExactly(
                                                        org.assertj.core.api.Assertions.tuple("c.lovedAt",
                                                                        Sort.Direction.DESC,
                                                                        Sort.NullHandling.NULLS_LAST),
                                                        org.assertj.core.api.Assertions.tuple("c.name",
                                                                        Sort.Direction.ASC,
                                                                        Sort.NullHandling.NATIVE));
                }

                private void stubCurator() {
                        when(staffUserRepository.findByIdAndActiveTrue(curator.getId()))
                                        .thenReturn(Optional.of(curator));
                }

                private void stubLovedUpdate() {
                        stubCurator();
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());
                        lenient().when(staffUserRepository.findAllByIdWithUser(List.of(curator.getId())))
                                        .thenReturn(List.of(curator));
                }
        }

        @Nested
        class Publish {

                private CampaignDifficulty node(int x, String acc, boolean terminal) {
                        return CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(Double.valueOf(acc))
                                        .terminal(terminal)
                                        .positionX((double) (x)).positionY((double) (0))
                                        .xp(0.0).active(true).build();
                }

                private void stubPublish(List<CampaignDifficulty> difficulties) {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(difficulties);
                }

                @Test
                void publishesWhenANodeIsFlaggedTerminal() {
                        stubPublish(List.of(node(0, "0.90", false), node(1, "0.95", true)));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.publish(campaign.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.PUBLISHED);
                        assertThat(result.getPublishedAt()).isNotNull();
                }

                @Test
                void publishesWithSeveralTerminalsAndDisconnectedBranches() {
                        stubPublish(List.of(node(0, "0.90", true), node(1, "0.95", true), node(2, "0.97", false)));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.publish(campaign.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.PUBLISHED);
                }

                @Test
                void rejectsWhenNoNodeIsFlaggedTerminal() {
                        stubPublish(List.of(node(0, "0.90", false), node(1, "0.95", false)));

                        assertThatThrownBy(() -> campaignService.publish(campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void ignoresTerminalFlagsWhenEveryNodeMustBeCleared() {
                        campaign.setCompletionMode(CampaignCompletionMode.ALL);
                        stubPublish(List.of(node(0, "0.90", false), node(1, "0.95", false)));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.publish(campaign.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.PUBLISHED);
                }

                @Test
                void rejectsWhenOnlyABarrierIsFlaggedTerminal() {
                        CampaignDifficulty gate = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).barrier(true).terminal(true)
                                        .positionX((double) (2)).positionY((double) (0))
                                        .xp(0.0).active(true).build();
                        stubPublish(List.of(node(0, "0.90", false), gate));

                        assertThatThrownBy(() -> campaignService.publish(campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void clearsDirtyNodesAndRecomputesProgress() {
                        CampaignDifficulty node = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.95)
                                        .terminal(true)
                                        .positionX((double) (0)).positionY((double) (0))
                                        .xp(0.0).active(true)
                                        .requirementDirty(true).build();

                        stubPublish(List.of(node));
                        when(campaignDifficultyRepository
                                        .findByCampaign_IdAndActiveTrueAndRequirementDirtyTrue(campaign.getId()))
                                        .thenReturn(List.of(node));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.publish(campaign.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.PUBLISHED);
                        assertThat(node.isRequirementDirty()).isFalse();
                        verify(campaignEvaluationService)
                                        .recomputeAfterRequirementChange(campaign, Set.of(node.getId()));
                }
        }

        @Nested
        class Unpublish {

                @Test
                void returnsPublishedCampaignToDraft() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService
                                        .unpublishAsEditor(CampaignEditor.player(creator.getId()), campaign.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.DRAFT);
                }

                @Test
                void rejectsNonOwner() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.unpublishAsEditor(CampaignEditor.player(999L),
                                        campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsWhenNotPublished() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService
                                        .unpublishAsEditor(CampaignEditor.player(creator.getId()), campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsCuratedCampaign() {
                        campaign.setStatus(CampaignStatus.CURATED);
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService
                                        .unpublishAsEditor(CampaignEditor.player(creator.getId()), campaign.getId()))
                                        .isInstanceOf(ValidationException.class);

                        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CURATED);
                }
        }

        @Nested
        class MarkCurated {

                @Test
                void rejectsNonCurator() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        StaffUser nonCurator = StaffUser.builder().id(UUID.randomUUID()).role(StaffRole.RANKING)
                                        .build();
                        when(staffUserRepository.findByIdAndActiveTrue(nonCurator.getId()))
                                        .thenReturn(Optional.of(nonCurator));

                        assertThatThrownBy(() -> campaignService.markCurated(campaign.getId(), nonCurator.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void promotesPublishedCampaignToCurated() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        StaffUser curator = StaffUser.builder().id(UUID.randomUUID()).role(StaffRole.CAMPAIGN_CURATOR)
                                        .build();
                        CampaignDifficulty single = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.90)
                                        .terminal(true)
                                        .positionX((double) (0)).positionY((double) (0))
                                        .xp(0.0).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(single));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());
                        when(staffUserRepository.findByIdAndActiveTrue(curator.getId()))
                                        .thenReturn(Optional.of(curator));
                        when(staffUserRepository.findAllByIdWithUser(List.of(curator.getId())))
                                        .thenReturn(List.of(curator));

                        CampaignResponse result = campaignService.markCurated(campaign.getId(), curator.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.CURATED);
                        assertThat(result.getCuratedBy()).isNotNull();
                        assertThat(result.getCuratedBy().getId()).isEqualTo(curator.getId());
                }
        }

        @Nested
        class AddDifficulty {

                @Test
                void rejectsPositionCollision() {
                        AddCampaignDifficultyRequest request = new AddCampaignDifficultyRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setRequirementType(CampaignRequirementType.ACC);
                        request.setRequirementValue(0.95);
                        request.setPositionX((double) (0));
                        request.setPositionY((double) (0));

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                                        campaign.getId(), 0.0, 0.0)).thenReturn(true);

                        assertThatThrownBy(() -> campaignService.addDifficulty(campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void addsDifficultyAtFreePosition() {
                        AddCampaignDifficultyRequest request = new AddCampaignDifficultyRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setRequirementType(CampaignRequirementType.ACC);
                        request.setRequirementValue(0.95);
                        request.setPositionX((double) (2));
                        request.setPositionY((double) (1));
                        request.setXp(100.0);

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                                        campaign.getId(), (double) (2), 1.0)).thenReturn(false);
                        when(campaignDifficultyRepository.save(any(CampaignDifficulty.class))).thenAnswer(inv -> {
                                CampaignDifficulty d = inv.getArgument(0);
                                d.setId(UUID.randomUUID());
                                return d;
                        });

                        CampaignDifficultyResponse result = campaignService.addDifficulty(campaign.getId(), request);

                        assertThat(result.getPositionX()).isEqualByComparingTo((double) (2));
                        assertThat(result.getPositionY()).isEqualByComparingTo((double) (1));
                        assertThat(result.getXp()).isEqualByComparingTo(100.0);
                }

                @Test
                void defaultsNodeBorderLayerToAboveWhenUnspecified() {
                        AddCampaignDifficultyRequest request = new AddCampaignDifficultyRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setRequirementType(CampaignRequirementType.ACC);
                        request.setRequirementValue(0.95);
                        request.setPositionX((double) (2));
                        request.setPositionY((double) (1));
                        request.setNodeBorderUrl("https://cdn.example/border.png");
                        stubAddDifficulty();

                        CampaignDifficultyResponse result = campaignService.addDifficulty(campaign.getId(), request);

                        assertThat(result.getNodeBorderUrl()).isEqualTo("https://cdn.example/border.png");
                        assertThat(result.getNodeBorderLayer()).isEqualTo(CampaignNodeBorderLayer.ABOVE);
                }

                @Test
                void honoursExplicitBelowNodeBorderLayer() {
                        AddCampaignDifficultyRequest request = new AddCampaignDifficultyRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setRequirementType(CampaignRequirementType.ACC);
                        request.setRequirementValue(0.95);
                        request.setPositionX((double) (2));
                        request.setPositionY((double) (1));
                        request.setNodeBorderUrl("https://cdn.example/border.png");
                        request.setNodeBorderLayer(CampaignNodeBorderLayer.BELOW);
                        stubAddDifficulty();

                        CampaignDifficultyResponse result = campaignService.addDifficulty(campaign.getId(), request);

                        assertThat(result.getNodeBorderLayer()).isEqualTo(CampaignNodeBorderLayer.BELOW);
                }

                @Test
                void acceptsFractionalNodePositions() {
                        AddCampaignDifficultyRequest request = new AddCampaignDifficultyRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setRequirementType(CampaignRequirementType.ACC);
                        request.setRequirementValue(0.95);
                        request.setPositionX(2.25);
                        request.setPositionY(-1.5);

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                                        campaign.getId(), 2.25, -1.5))
                                        .thenReturn(false);
                        when(campaignDifficultyRepository.save(any(CampaignDifficulty.class))).thenAnswer(inv -> {
                                CampaignDifficulty d = inv.getArgument(0);
                                d.setId(UUID.randomUUID());
                                return d;
                        });

                        CampaignDifficultyResponse result = campaignService.addDifficulty(campaign.getId(), request);

                        assertThat(result.getPositionX()).isEqualByComparingTo(2.25);
                        assertThat(result.getPositionY()).isEqualByComparingTo(-1.5);
                }

                @Test
                void clearsTheUpperBoundOnRequest() {
                        CampaignDifficulty node = boundedNode(0.90, 0.95);
                        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
                        request.setClear(Set.of(CampaignBound.VALUE_MAX));
                        stubNodeUpdate(node);

                        CampaignDifficultyResponse result = campaignService.updateDifficulty(node.getId(), request);

                        assertThat(result.getRequirementValue()).isEqualByComparingTo(0.90);
                        assertThat(result.getRequirementValueMax()).isNull();
                }

                @Test
                void clearsTheLowerBoundLeavingACapOnlyRequirement() {
                        CampaignDifficulty node = boundedNode(1.0, 3.0);
                        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
                        request.setClear(Set.of(CampaignBound.VALUE));
                        stubNodeUpdate(node);

                        CampaignDifficultyResponse result = campaignService.updateDifficulty(node.getId(), request);

                        assertThat(result.getRequirementValue()).isNull();
                        assertThat(result.getRequirementValueMax()).isEqualByComparingTo(3.0);
                }

                @Test
                void rejectsClearingBothBounds() {
                        CampaignDifficulty node = boundedNode(0.90, 0.95);
                        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
                        request.setClear(Set.of(CampaignBound.VALUE, CampaignBound.VALUE_MAX));
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));

                        assertThatThrownBy(() -> campaignService.updateDifficulty(node.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsSettingAndClearingTheSameBound() {
                        CampaignDifficulty node = boundedNode(0.90, 0.95);
                        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
                        request.setRequirementValueMax(0.99);
                        request.setClear(Set.of(CampaignBound.VALUE_MAX));
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));

                        assertThatThrownBy(() -> campaignService.updateDifficulty(node.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                private CampaignDifficulty boundedNode(Double value, Double valueMax) {
                        return CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(value).requirementValueMax(valueMax)
                                        .positionX(0.0).positionY(0.0)
                                        .xp(0.0).active(true).build();
                }

                private void stubNodeUpdate(CampaignDifficulty node) {
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(campaignDifficultyRepository.save(any(CampaignDifficulty.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));
                        when(campaignDifficultyPathRepository.findByCampaignDifficulty_IdAndActiveTrue(node.getId()))
                                        .thenReturn(List.of());
                }

                @Test
                void treatsAPositionDifferingOnlyInScaleAsUnmoved() {
                        CampaignDifficulty node = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.95)
                                        .positionX(2).positionY(1.0)
                                        .xp(0.0).active(true).build();
                        UpdateCampaignDifficultyRequest request = new UpdateCampaignDifficultyRequest();
                        request.setPositionX(2.00);
                        request.setPositionY(1.000);

                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(campaignDifficultyRepository.save(any(CampaignDifficulty.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));
                        when(campaignDifficultyPathRepository.findByCampaignDifficulty_IdAndActiveTrue(node.getId()))
                                        .thenReturn(List.of());

                        campaignService.updateDifficulty(node.getId(), request);

                        verify(campaignDifficultyRepository, never())
                                        .existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(any(), any(), any());
                }

                private void stubAddDifficulty() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                                        campaign.getId(), (double) (2), 1.0)).thenReturn(false);
                        when(campaignDifficultyRepository.save(any(CampaignDifficulty.class))).thenAnswer(inv -> {
                                CampaignDifficulty d = inv.getArgument(0);
                                d.setId(UUID.randomUUID());
                                return d;
                        });
                }
        }

        @Nested
        class StartCampaign {

                @Test
                void rejectsDraftCampaign() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.startCampaign(creator.getId(), campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void createsNewUserCampaign() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userRepository.findByIdAndActiveTrue(creator.getId())).thenReturn(Optional.of(creator));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(creator.getId(),
                                        campaign.getId()))
                                        .thenReturn(Optional.empty());
                        when(userCampaignRepository.save(any(UserCampaign.class))).thenAnswer(inv -> {
                                UserCampaign uc = inv.getArgument(0);
                                uc.setId(UUID.randomUUID());
                                return uc;
                        });

                        UserCampaignResponse result = campaignService.startCampaign(creator.getId(), campaign.getId());

                        assertThat(result.getProgressStatus()).isEqualTo(UserCampaignStatus.IN_PROGRESS);
                        assertThat(result.getCampaign().getId()).isEqualTo(campaign.getId());
                }

                @Test
                void revivingAbandonedCampaignKeepsCompletionRewardsPaid() {
                        campaign.setStatus(CampaignStatus.CURATED);
                        UserCampaign abandoned = UserCampaign.builder().id(UUID.randomUUID())
                                        .user(creator).campaign(campaign)
                                        .status(UserCampaignStatus.ABANDONED)
                                        .completionRewardsPaid(true)
                                        .completedAt(java.time.Instant.now())
                                        .active(true).build();
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userRepository.findByIdAndActiveTrue(creator.getId())).thenReturn(Optional.of(creator));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(
                                        creator.getId(), campaign.getId())).thenReturn(Optional.of(abandoned));
                        when(userCampaignRepository.save(any(UserCampaign.class)))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        UserCampaignResponse result = campaignService.startCampaign(creator.getId(), campaign.getId());

                        assertThat(result.getProgressStatus()).isEqualTo(UserCampaignStatus.IN_PROGRESS);
                        assertThat(abandoned.isCompletionRewardsPaid()).isTrue();
                        assertThat(abandoned.getCompletedAt()).isNull();
                }
        }

        @Nested
        class GetUserProgress {

                @Test
                void computesAccProgressForSingleDifficulty() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        CampaignDifficulty d = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.90)
                                        .positionX((double) (0)).positionY((double) (0))
                                        .xp(0.0).active(true).build();
                        Score score = Score.builder()
                                        .id(UUID.randomUUID()).user(creator).mapDifficulty(mapDifficulty)
                                        .score(950000).scoreNoMods(950000)
                                        .timeSet(java.time.Instant.ofEpochSecond(2_000)).build();
                        com.accsaber.backend.model.entity.campaign.UserCampaign uc = com.accsaber.backend.model.entity.campaign.UserCampaign
                                        .builder().id(UUID.randomUUID()).user(creator).campaign(campaign)
                                        .status(com.accsaber.backend.model.entity.campaign.UserCampaignStatus.IN_PROGRESS)
                                        .startedAt(java.time.Instant.ofEpochSecond(1_000)).active(true).build();
                        com.accsaber.backend.model.entity.campaign.UserCampaignScore ucs = com.accsaber.backend.model.entity.campaign.UserCampaignScore
                                        .builder().id(UUID.randomUUID()).user(creator).campaign(campaign)
                                        .campaignDifficulty(d).score(score).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of(uc));
                        when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(
                                        eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of(ucs));
                        when(campaignDifficultyRepository.findActiveWithMapByCampaignIds(anyCollection()))
                                        .thenReturn(List.of(d));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdInAndActiveTrue(anyCollection()))
                                        .thenReturn(List.of());
                        when(scoreRepository.findEligibleCampaignRows(eq(creator.getId()), anyCollection(),
                                        any()))
                                        .thenReturn(List.of(score));
                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getCampaign().getDifficultyCount()).isEqualTo(1);
                        assertThat(result.getCompletedDifficulties()).isEqualTo(1);
                        assertThat(result.getDifficulties().get(0).isCompleted()).isTrue();
                        assertThat(result.getDifficulties().get(0).getUserValue())
                                        .isEqualByComparingTo(0.95);
                }

                @Test
                void showsZeroProgressWhenNotStarted() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        CampaignDifficulty d = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.90)
                                        .positionX((double) (0)).positionY((double) (0))
                                        .xp(0.0).active(true).build();
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of());
                        when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(
                                        eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of());
                        when(campaignDifficultyRepository.findActiveWithMapByCampaignIds(anyCollection()))
                                        .thenReturn(List.of(d));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdInAndActiveTrue(anyCollection()))
                                        .thenReturn(List.of());
                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getCompletedDifficulties()).isEqualTo(0);
                        assertThat(result.getDifficulties().get(0).isCompleted()).isFalse();
                        assertThat(result.getDifficulties().get(0).getUserValue()).isNull();
                }

                @Test
                void respectsProgressionAgnostic() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        campaign.setProgressionAgnostic(true);
                        CampaignDifficulty a = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.90)
                                        .positionX((double) (0)).positionY((double) (0))
                                        .xp(0.0).active(true).build();
                        CampaignDifficulty b = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.95)
                                        .positionX((double) (1)).positionY((double) (0))
                                        .xp(0.0).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of());
                        when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(
                                        eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of());
                        when(campaignDifficultyRepository.findActiveWithMapByCampaignIds(anyCollection()))
                                        .thenReturn(List.of(a, b));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdInAndActiveTrue(anyCollection()))
                                        .thenReturn(List.of(
                                                        com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath
                                                                        .builder()
                                                                        .id(UUID.randomUUID()).campaignDifficulty(b)
                                                                        .comesFromCampaignDifficulty(a).active(true)
                                                                        .build()));
                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getDifficulties().get(0).isUnlocked()).isTrue();
                        assertThat(result.getDifficulties().get(1).isUnlocked()).isTrue();
                }
        }

        @Nested
        class DeactivateCampaign {

                @Test
                void deactivates() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

                        campaignService.deactivateCampaign(campaign.getId());

                        assertThat(campaign.isActive()).isFalse();
                }

                @Test
                void throwsWhenMissing() {
                        UUID id = UUID.randomUUID();
                        when(campaignRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> campaignService.deactivateCampaign(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class CollaboratorEditing {

                @Test
                void allowsAcceptedCollaboratorToEditDraft() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Renamed");
                        Long collaboratorId = 777L;

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignCollaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                                        campaign.getId(), collaboratorId, CampaignCollaboratorStatus.ACCEPTED))
                                        .thenReturn(true);
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.updateCampaignAsEditor(
                                        CampaignEditor.player(collaboratorId),
                                        campaign.getId(), request);

                        assertThat(result.getName()).isEqualTo("Renamed");
                }

                @Test
                void rejectsNonOwnerNonCollaboratorEdit() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Nope");
                        Long strangerId = 888L;

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignCollaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                                        campaign.getId(), strangerId, CampaignCollaboratorStatus.ACCEPTED))
                                        .thenReturn(false);

                        assertThatThrownBy(
                                        () -> campaignService.updateCampaignAsEditor(CampaignEditor.player(strangerId),
                                                        campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsCollaboratorEditWhenNotDraft() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Nope");
                        Long collaboratorId = 777L;

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignCollaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                                        campaign.getId(), collaboratorId, CampaignCollaboratorStatus.ACCEPTED))
                                        .thenReturn(true);

                        assertThatThrownBy(() -> campaignService.updateCampaignAsEditor(
                                        CampaignEditor.player(collaboratorId),
                                        campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void staffEditorSkipsOwnershipAndDraftChecks() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Curated rename");

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.updateCampaignAsEditor(
                                        CampaignEditor.staff(999L), campaign.getId(), request);

                        assertThat(result.getName()).isEqualTo("Curated rename");
                        verify(campaignCollaboratorRepository, never())
                                        .existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(any(), any(), any());
                }

                @Test
                void staffEditorIsNotHeldToTheCdnImageryRule() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setIconUrl("https://somewhere-else.example/icon.png");

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        assertThatCode(() -> campaignService.updateCampaignAsEditor(
                                        CampaignEditor.staff(999L), campaign.getId(), request))
                                        .doesNotThrowAnyException();
                }

                @Test
                void playerIsStillHeldToTheCdnImageryRule() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setIconUrl("https://somewhere-else.example/icon.png");

                        assertThatThrownBy(() -> campaignService.updateCampaignAsEditor(
                                        CampaignEditor.player(creator.getId()), campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }
        }

        @Nested
        class AbandonAndList {

                @Test
                void abandonMarksUserCampaignAbandoned() {
                        UserCampaign uc = UserCampaign.builder().id(UUID.randomUUID())
                                        .user(creator).campaign(campaign)
                                        .status(UserCampaignStatus.IN_PROGRESS).active(true).build();
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(
                                        creator.getId(), campaign.getId())).thenReturn(Optional.of(uc));

                        campaignService.abandonCampaign(creator.getId(), campaign.getId());

                        assertThat(uc.getStatus()).isEqualTo(UserCampaignStatus.ABANDONED);
                        verify(userCampaignRepository).save(uc);
                }

                @Test
                void cannotAbandonCompletedCampaign() {
                        UserCampaign completed = UserCampaign.builder().id(UUID.randomUUID())
                                        .user(creator).campaign(campaign)
                                        .status(UserCampaignStatus.COMPLETED).active(true).build();
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdAndActiveTrue(
                                        creator.getId(), campaign.getId())).thenReturn(Optional.of(completed));

                        assertThatThrownBy(() -> campaignService.abandonCampaign(creator.getId(), campaign.getId()))
                                        .isInstanceOf(ValidationException.class);

                        assertThat(completed.getStatus()).isEqualTo(UserCampaignStatus.COMPLETED);
                }

                @Test
                void listExcludesAbandonedCampaigns() {
                        when(campaignRepository.findFiltered(anyBoolean(), any(), any(), anyBoolean(), any(), any(),
                                        any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any()))
                                        .thenReturn(new PageImpl<>(List.of()));

                        campaignService.listUserCampaigns(
                                        CampaignFilter.builder().participantId(creator.getId()).build(),
                                        null, false, PageRequest.of(0, 20));

                        verify(campaignRepository).findFiltered(anyBoolean(), any(), any(), anyBoolean(), any(), any(),
                                        any(), anyBoolean(), any(), any(), any(), any(), eq(creator.getId()),
                                        eq(List.of(UserCampaignStatus.IN_PROGRESS, UserCampaignStatus.COMPLETED)),
                                        any());
                }

                @Test
                void listReportsProgressAlongsideTheCampaign() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        UserCampaign uc = UserCampaign.builder().id(UUID.randomUUID())
                                        .user(creator).campaign(campaign)
                                        .status(UserCampaignStatus.IN_PROGRESS).active(true).build();
                        when(campaignRepository.findFiltered(anyBoolean(), any(), any(), anyBoolean(), any(), any(),
                                        any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any()))
                                        .thenReturn(new PageImpl<>(List.of(campaign)));
                        when(campaignTagLinkRepository.findByCampaign_IdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignDifficultyRepository.countActiveByCampaignIds(List.of(campaign.getId())))
                                        .thenReturn(List.<Object[]>of(new Object[] { campaign.getId(), 14L }));
                        when(campaignCompletionItemRepository.findByCampaign_IdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignDifficultyItemRepository.findActiveByCampaignIds(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(campaignRewardTotalsRepository.findByCampaignIdIn(List.of(campaign.getId())))
                                        .thenReturn(List.of());
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(
                                        creator.getId(), List.of(campaign.getId()))).thenReturn(List.of(uc));
                        when(userCampaignScoreRepository.countActiveByUserAndCampaignIds(
                                        creator.getId(), List.of(campaign.getId())))
                                        .thenReturn(List.<Object[]>of(new Object[] { campaign.getId(), 12L }));

                        Page<UserCampaignResponse> page = campaignService.listUserCampaigns(
                                        CampaignFilter.builder().participantId(creator.getId()).build(),
                                        null, false, PageRequest.of(0, 20));

                        assertThat(page.getContent()).hasSize(1);
                        UserCampaignResponse result = page.getContent().get(0);
                        assertThat(result.getProgressStatus()).isEqualTo(UserCampaignStatus.IN_PROGRESS);
                        assertThat(result.getCompletedDifficulties()).isEqualTo(12);
                        assertThat(result.getCampaign().getDifficultyCount()).isEqualTo(14);
                }
        }

        @Nested
        class OfficialAndItemRewards {

                private CampaignDifficulty draftNode() {
                        return CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(0.90)
                                        .positionX((double) (0)).positionY((double) (0))
                                        .xp(0.0).active(true).build();
                }

                @Test
                void rejectsUntradeableItemOnNonOfficialCampaign() {
                        CampaignDifficulty node = draftNode();
                        Item untradeable = Item.builder().id(UUID.randomUUID()).tradeable(false).build();
                        SetCampaignItemRequest request = new SetCampaignItemRequest();
                        request.setItemId(untradeable.getId());
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(itemRepository.findByIdAndActiveTrue(untradeable.getId()))
                                        .thenReturn(Optional.of(untradeable));

                        assertThatThrownBy(() -> campaignService.setDifficultyItemAsEditor(
                                        CampaignEditor.player(creator.getId()), node.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void allowsUntradeableItemOnOfficialCampaign() {
                        campaign.setOfficial(true);
                        CampaignDifficulty node = draftNode();
                        Item untradeable = Item.builder().id(UUID.randomUUID()).tradeable(false).build();
                        SetCampaignItemRequest request = new SetCampaignItemRequest();
                        request.setItemId(untradeable.getId());
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(itemRepository.findByIdAndActiveTrue(untradeable.getId()))
                                        .thenReturn(Optional.of(untradeable));
                        when(campaignDifficultyItemRepository.findById(any())).thenReturn(Optional.empty());
                        when(campaignDifficultyItemRepository.findByCampaignDifficulty_Id(node.getId()))
                                        .thenReturn(List.of());

                        campaignService.setDifficultyItemAsEditor(CampaignEditor.player(creator.getId()), node.getId(),
                                        request);

                        verify(campaignDifficultyItemRepository).save(any());
                }

                @Test
                void allowsRandomActiveCrateOnNonOfficialCampaign() {
                        CampaignDifficulty node = draftNode();
                        Item sentinel = Item.builder().id(UUID.randomUUID()).name("Random Active Crate")
                                        .tradeable(false)
                                        .value(new ObjectMapper().createObjectNode().put("grant", "active_crate"))
                                        .build();
                        SetCampaignItemRequest request = new SetCampaignItemRequest();
                        request.setItemId(sentinel.getId());
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(itemRepository.findByIdAndActiveTrue(sentinel.getId()))
                                        .thenReturn(Optional.of(sentinel));
                        when(campaignDifficultyItemRepository.findById(any())).thenReturn(Optional.empty());
                        when(campaignDifficultyItemRepository.findByCampaignDifficulty_Id(node.getId()))
                                        .thenReturn(List.of());

                        campaignService.setDifficultyItemAsEditor(CampaignEditor.player(creator.getId()), node.getId(),
                                        request);

                        verify(campaignDifficultyItemRepository).save(any());
                }

                @Test
                void allowsTradeableItemOnNonOfficialCampaign() {
                        CampaignDifficulty node = draftNode();
                        Item tradeable = Item.builder().id(UUID.randomUUID()).tradeable(true).build();
                        SetCampaignItemRequest request = new SetCampaignItemRequest();
                        request.setItemId(tradeable.getId());
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(itemRepository.findByIdAndActiveTrue(tradeable.getId()))
                                        .thenReturn(Optional.of(tradeable));
                        when(campaignDifficultyItemRepository.findById(any())).thenReturn(Optional.empty());
                        when(campaignDifficultyItemRepository.findByCampaignDifficulty_Id(node.getId()))
                                        .thenReturn(List.of());

                        campaignService.setDifficultyItemAsEditor(CampaignEditor.player(creator.getId()), node.getId(),
                                        request);

                        verify(campaignDifficultyItemRepository).save(any());
                }

                @Test
                void rejectsItemPastItsObtainableCutoff() {
                        CampaignDifficulty node = draftNode();
                        Item expired = Item.builder().id(UUID.randomUUID()).name("Alpha Crate").tradeable(true)
                                        .obtainableUntil(Instant.now().minusSeconds(60)).build();
                        SetCampaignItemRequest request = new SetCampaignItemRequest();
                        request.setItemId(expired.getId());
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(itemRepository.findByIdAndActiveTrue(expired.getId()))
                                        .thenReturn(Optional.of(expired));

                        assertThatThrownBy(() -> campaignService.setDifficultyItemAsEditor(
                                        CampaignEditor.player(creator.getId()), node.getId(), request))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Alpha Crate");

                        verify(campaignDifficultyItemRepository, never()).save(any());
                }

                @Test
                void allowsItemWhoseObtainableCutoffHasNotPassed() {
                        CampaignDifficulty node = draftNode();
                        Item stillOpen = Item.builder().id(UUID.randomUUID()).name("Alpha Crate").tradeable(true)
                                        .obtainableUntil(Instant.now().plusSeconds(3600)).build();
                        SetCampaignItemRequest request = new SetCampaignItemRequest();
                        request.setItemId(stillOpen.getId());
                        when(campaignDifficultyRepository.findByIdAndActiveTrue(node.getId()))
                                        .thenReturn(Optional.of(node));
                        when(itemRepository.findByIdAndActiveTrue(stillOpen.getId()))
                                        .thenReturn(Optional.of(stillOpen));
                        when(campaignDifficultyItemRepository.findById(any())).thenReturn(Optional.empty());
                        when(campaignDifficultyItemRepository.findByCampaignDifficulty_Id(node.getId()))
                                        .thenReturn(List.of());

                        campaignService.setDifficultyItemAsEditor(CampaignEditor.player(creator.getId()), node.getId(),
                                        request);

                        verify(campaignDifficultyItemRepository).save(any());
                }

                @Test
                void rejectsItemPastItsObtainableCutoffOnCompletionRewards() {
                        Item expired = Item.builder().id(UUID.randomUUID()).name("Alpha Crate").tradeable(true)
                                        .obtainableUntil(Instant.now().minusSeconds(60)).build();
                        SetCampaignItemRequest request = new SetCampaignItemRequest();
                        request.setItemId(expired.getId());
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(itemRepository.findByIdAndActiveTrue(expired.getId()))
                                        .thenReturn(Optional.of(expired));

                        assertThatThrownBy(() -> campaignService.setCompletionItemAsEditor(
                                        CampaignEditor.player(creator.getId()), campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("Alpha Crate");

                        verify(campaignCompletionItemRepository, never()).save(any());
                }

                @Test
                void setOfficialMarksCampaignOfficialAndExposesIt() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.setOfficial(campaign.getId(), true);

                        assertThat(campaign.isOfficial()).isTrue();
                        assertThat(result.isOfficial()).isTrue();
                }
        }

        @Nested
        class EditorCaps {

                @Test
                void playerHitsTheDifficultyCap() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignDifficultyRepository
                                        .countByCampaign_IdAndBarrierFalseAndActiveTrue(campaign.getId()))
                                        .thenReturn(200L);

                        assertThatThrownBy(() -> campaignService.addDifficultyAsEditor(
                                        CampaignEditor.player(creator.getId()), campaign.getId(),
                                        new AddCampaignDifficultyRequest()))
                                        .isInstanceOf(ValidationException.class)
                                        .hasMessageContaining("maximum");
                }

                @Test
                void staffEditorIsNotCappedOnDifficulties() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.addDifficultyAsEditor(
                                        CampaignEditor.staff(999L), campaign.getId(),
                                        new AddCampaignDifficultyRequest()))
                                        .isNotInstanceOf(ValidationException.class);

                        verify(campaignDifficultyRepository, never())
                                        .countByCampaign_IdAndBarrierFalseAndActiveTrue(any());
                }
        }
}
