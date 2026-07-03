package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignDifficultyRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDifficultyResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.dto.response.campaign.UserCampaignResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignRequirementType;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaign;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.campaign.CampaignBarrierAffectedDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignCompletionItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyItemRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyPathRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.CampaignTagLinkRepository;
import com.accsaber.backend.repository.campaign.CampaignTagRepository;
import com.accsaber.backend.repository.campaign.CampaignTextRepository;
import com.accsaber.backend.repository.campaign.CampaignVoteRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignScoreRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.RichTextSanitizer;

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
        private UserRepository userRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private DuplicateUserService duplicateUserService;
        @Mock
        private CampaignEvaluationService campaignEvaluationService;
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
                                .completionXp(BigDecimal.ZERO)
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
                void rejectsUpdateWhenCurated() {
                        campaign.setStatus(CampaignStatus.CURATED);
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Locked");

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.updateCampaign(campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }
        }

        @Nested
        class Publish {

                @Test
                void publishesWhenSingleSink() {
                        CampaignDifficulty a = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.90"))
                                        .positionX(0).positionY(0).xp(BigDecimal.ZERO).active(true).build();
                        CampaignDifficulty b = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.95"))
                                        .positionX(1).positionY(0).xp(BigDecimal.ZERO).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(a, b));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(
                                                        com.accsaber.backend.model.entity.campaign.CampaignDifficultyPath
                                                                        .builder()
                                                                        .id(UUID.randomUUID()).campaignDifficulty(b)
                                                                        .comesFromCampaignDifficulty(a).active(true)
                                                                        .build()));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.publish(campaign.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.PUBLISHED);
                }

                @Test
                void rejectsWhenMultipleSinks() {
                        CampaignDifficulty a = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.90"))
                                        .positionX(0).positionY(0).xp(BigDecimal.ZERO).active(true).build();
                        CampaignDifficulty b = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.95"))
                                        .positionX(1).positionY(0).xp(BigDecimal.ZERO).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(a, b));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());

                        assertThatThrownBy(() -> campaignService.publish(campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void clearsDirtyNodesAndRecomputesProgress() {
                        CampaignDifficulty node = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.95"))
                                        .positionX(0).positionY(0).xp(BigDecimal.ZERO).active(true)
                                        .requirementDirty(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(node));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());
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

                        CampaignResponse result = campaignService.unpublishAsPlayer(creator.getId(), campaign.getId());

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.DRAFT);
                }

                @Test
                void rejectsNonOwner() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.unpublishAsPlayer(999L, campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsWhenNotPublished() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.unpublishAsPlayer(creator.getId(), campaign.getId()))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void rejectsCuratedCampaign() {
                        campaign.setStatus(CampaignStatus.CURATED);
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));

                        assertThatThrownBy(() -> campaignService.unpublishAsPlayer(creator.getId(), campaign.getId()))
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

                        assertThatThrownBy(() -> campaignService.markCurated(campaign.getId(), nonCurator))
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
                                        .requirementValue(new BigDecimal("0.90"))
                                        .positionX(0).positionY(0).xp(BigDecimal.ZERO).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignDifficultyRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(single));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
                        when(campaignTagLinkRepository.findByCampaign_Id(any())).thenReturn(List.of());

                        CampaignResponse result = campaignService.markCurated(campaign.getId(), curator);

                        assertThat(result.getStatus()).isEqualTo(CampaignStatus.CURATED);
                }
        }

        @Nested
        class AddDifficulty {

                @Test
                void rejectsPositionCollision() {
                        AddCampaignDifficultyRequest request = new AddCampaignDifficultyRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setRequirementType(CampaignRequirementType.ACC);
                        request.setRequirementValue(new BigDecimal("0.95"));
                        request.setPositionX(0);
                        request.setPositionY(0);

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                                        campaign.getId(), 0, 0)).thenReturn(true);

                        assertThatThrownBy(() -> campaignService.addDifficulty(campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }

                @Test
                void addsDifficultyAtFreePosition() {
                        AddCampaignDifficultyRequest request = new AddCampaignDifficultyRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setRequirementType(CampaignRequirementType.ACC);
                        request.setRequirementValue(new BigDecimal("0.95"));
                        request.setPositionX(2);
                        request.setPositionY(1);
                        request.setXp(new BigDecimal("100"));

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignDifficultyRepository.existsByCampaign_IdAndPositionXAndPositionYAndActiveTrue(
                                        campaign.getId(), 2, 1)).thenReturn(false);
                        when(campaignDifficultyRepository
                                        .findByCampaign_IdAndMapDifficulty_IdAndActiveTrue(campaign.getId(),
                                                        mapDifficulty.getId()))
                                        .thenReturn(Optional.empty());
                        when(campaignDifficultyRepository.save(any(CampaignDifficulty.class))).thenAnswer(inv -> {
                                CampaignDifficulty d = inv.getArgument(0);
                                d.setId(UUID.randomUUID());
                                return d;
                        });

                        CampaignDifficultyResponse result = campaignService.addDifficulty(campaign.getId(), request);

                        assertThat(result.getPositionX()).isEqualTo(2);
                        assertThat(result.getPositionY()).isEqualTo(1);
                        assertThat(result.getXp()).isEqualByComparingTo(new BigDecimal("100"));
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
        }

        @Nested
        class GetUserProgress {

                @Test
                void computesAccProgressForSingleDifficulty() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        CampaignDifficulty d = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.90"))
                                        .positionX(0).positionY(0).xp(BigDecimal.ZERO).active(true).build();
                        Score score = Score.builder()
                                        .id(UUID.randomUUID()).user(creator).mapDifficulty(mapDifficulty)
                                        .score(950000).scoreNoMods(950000).build();
                        com.accsaber.backend.model.entity.campaign.UserCampaign uc = com.accsaber.backend.model.entity.campaign.UserCampaign
                                        .builder().id(UUID.randomUUID()).user(creator).campaign(campaign)
                                        .status(com.accsaber.backend.model.entity.campaign.UserCampaignStatus.IN_PROGRESS)
                                        .startedAt(java.time.Instant.now()).active(true).build();
                        com.accsaber.backend.model.entity.campaign.UserCampaignScore ucs = com.accsaber.backend.model.entity.campaign.UserCampaignScore
                                        .builder().id(UUID.randomUUID()).user(creator).campaign(campaign)
                                        .campaignDifficulty(d).score(score).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of(uc));
                        when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of(ucs));
                        when(campaignDifficultyRepository.findActiveWithMapByCampaignIds(anyCollection()))
                                        .thenReturn(List.of(d));
                        when(campaignDifficultyPathRepository
                                        .findByCampaignDifficulty_Campaign_IdInAndActiveTrue(anyCollection()))
                                        .thenReturn(List.of());
                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getCampaign().getDifficultyCount()).isEqualTo(1);
                        assertThat(result.getCompletedDifficulties()).isEqualTo(1);
                        assertThat(result.getDifficulties().get(0).isCompleted()).isTrue();
                        assertThat(result.getDifficulties().get(0).getUserValue())
                                        .isEqualByComparingTo(new BigDecimal("0.95"));
                }

                @Test
                void showsZeroProgressWhenNotStarted() {
                        campaign.setStatus(CampaignStatus.PUBLISHED);
                        CampaignDifficulty d = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.90"))
                                        .positionX(0).positionY(0).xp(BigDecimal.ZERO).active(true).build();
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of());
                        when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
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
                                        .requirementValue(new BigDecimal("0.90"))
                                        .positionX(0).positionY(0).xp(BigDecimal.ZERO).active(true).build();
                        CampaignDifficulty b = CampaignDifficulty.builder()
                                        .id(UUID.randomUUID()).campaign(campaign).mapDifficulty(mapDifficulty)
                                        .requirementType(CampaignRequirementType.ACC)
                                        .requirementValue(new BigDecimal("0.95"))
                                        .positionX(1).positionY(0).xp(BigDecimal.ZERO).active(true).build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(userCampaignRepository.findByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
                                        anyCollection()))
                                        .thenReturn(List.of());
                        when(userCampaignScoreRepository.findWithScoreByUser_IdAndCampaign_IdInAndActiveTrue(eq(creator.getId()),
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

                        CampaignResponse result = campaignService.updateCampaignAsPlayer(collaboratorId,
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

                        assertThatThrownBy(() -> campaignService.updateCampaignAsPlayer(strangerId,
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

                        assertThatThrownBy(() -> campaignService.updateCampaignAsPlayer(collaboratorId,
                                        campaign.getId(), request))
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
                void listExcludesAbandonedCampaigns() {
                        when(userCampaignRepository.findActiveByUserExcludingStatus(eq(creator.getId()),
                                        eq(UserCampaignStatus.ABANDONED), any())).thenReturn(Page.<UserCampaign>empty());

                        campaignService.listUserCampaigns(creator.getId(), PageRequest.of(0, 20));

                        verify(userCampaignRepository).findActiveByUserExcludingStatus(
                                        creator.getId(), UserCampaignStatus.ABANDONED, PageRequest.of(0, 20));
                }
        }
}
