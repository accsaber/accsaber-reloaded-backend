package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignMapRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignMapResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignMap;
import com.accsaber.backend.model.entity.campaign.CampaignMapPath;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignMapPathRepository;
import com.accsaber.backend.repository.campaign.CampaignMapRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

        @Mock
        private CampaignRepository campaignRepository;
        @Mock
        private CampaignMapRepository campaignMapRepository;
        @Mock
        private CampaignMapPathRepository campaignMapPathRepository;
        @Mock
        private ScoreRepository scoreRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;

        @InjectMocks
        private CampaignService campaignService;

        private User creator;
        private Campaign campaign;
        private com.accsaber.backend.model.entity.map.Map map;
        private MapDifficulty mapDifficulty;

        @BeforeEach
        void setUp() {
                creator = User.builder().id(12345L).name("TestPlayer").build();
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
                                .description("A test campaign")
                                .difficulty("Hard")
                                .verified(false)
                                .active(true)
                                .campaignMaps(List.of())
                                .build();
        }

        private final Pageable defaultPageable = PageRequest.of(0, 20, Sort.by("name"));

        @Nested
        class FindAllActiveCampaigns {

                @Test
                void returnsAllActiveCampaigns() {
                        when(campaignRepository.findByActiveTrue(defaultPageable))
                                        .thenReturn(new PageImpl<>(List.of(campaign), defaultPageable, 1));

                        Page<CampaignResponse> result = campaignService.findAllActiveCampaigns(defaultPageable);

                        assertThat(result.getContent()).hasSize(1);
                        assertThat(result.getContent().get(0).getName()).isEqualTo("Test Campaign");
                        assertThat(result.getContent().get(0).getCreatorName()).isEqualTo("TestPlayer");
                }

                @Test
                void returnsEmptyPageWhenNoCampaigns() {
                        when(campaignRepository.findByActiveTrue(defaultPageable))
                                        .thenReturn(new PageImpl<>(List.of(), defaultPageable, 0));

                        Page<CampaignResponse> result = campaignService.findAllActiveCampaigns(defaultPageable);

                        assertThat(result.getContent()).isEmpty();
                }
        }

        @Nested
        class FindCampaignById {

                @Test
                void returnsCampaignWithMaps() {
                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.95"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(cm));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());

                        CampaignDetailResponse result = campaignService.findCampaignById(campaign.getId());

                        assertThat(result.getName()).isEqualTo("Test Campaign");
                        assertThat(result.getMaps()).hasSize(1);
                        assertThat(result.getMaps().get(0).getSongName()).isEqualTo("Test Song");
                        assertThat(result.getMaps().get(0).getPrerequisiteMapIds()).isEmpty();
                }

                @Test
                void throwsWhenCampaignNotFound() {
                        UUID id = UUID.randomUUID();
                        when(campaignRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> campaignService.findCampaignById(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class GetUserProgress {

                @Test
                void computesProgressWithCompletedMap() {
                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        Score score = Score.builder()
                                        .id(UUID.randomUUID())
                                        .user(creator)
                                        .mapDifficulty(mapDifficulty)
                                        .score(950000) // 0.95 accuracy > 0.90 requirement
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(cm));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(creator.getId()),
                                        anyList()))
                                        .thenReturn(List.of(score));

                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getTotalMaps()).isEqualTo(1);
                        assertThat(result.getCompletedMaps()).isEqualTo(1);
                        assertThat(result.getMaps().get(0).isCompleted()).isTrue();
                        assertThat(result.getMaps().get(0).isUnlocked()).isTrue();
                        assertThat(result.getMaps().get(0).getUserAccuracy())
                                        .isEqualByComparingTo(new BigDecimal("0.95"));
                }

                @Test
                void computesProgressWithIncompleteMap() {
                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.98"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        Score score = Score.builder()
                                        .id(UUID.randomUUID())
                                        .user(creator)
                                        .mapDifficulty(mapDifficulty)
                                        .score(950000) // 0.95 accuracy < 0.98 requirement
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(cm));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(creator.getId()),
                                        anyList()))
                                        .thenReturn(List.of(score));

                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getCompletedMaps()).isEqualTo(0);
                        assertThat(result.getMaps().get(0).isCompleted()).isFalse();
                }

                @Test
                void handlesNoScoreForMap() {
                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(cm));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(creator.getId()),
                                        anyList()))
                                        .thenReturn(List.of());

                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getCompletedMaps()).isEqualTo(0);
                        assertThat(result.getMaps().get(0).getUserAccuracy()).isNull();
                        assertThat(result.getMaps().get(0).getUserScore()).isNull();
                        assertThat(result.getMaps().get(0).isCompleted()).isFalse();
                }

                @Test
                void handlesNullMaxScore() {
                        MapDifficulty mdNoMax = MapDifficulty.builder()
                                        .id(UUID.randomUUID())
                                        .map(map)
                                        .difficulty(Difficulty.EXPERT_PLUS)
                                        .characteristic("Standard")
                                        .maxScore(null)
                                        .build();

                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mdNoMax)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        Score score = Score.builder()
                                        .id(UUID.randomUUID())
                                        .user(creator)
                                        .mapDifficulty(mdNoMax)
                                        .score(950000)
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(cm));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(creator.getId()),
                                        anyList()))
                                        .thenReturn(List.of(score));

                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getMaps().get(0).getUserAccuracy()).isNull();
                        assertThat(result.getMaps().get(0).isCompleted()).isFalse();
                }

                @Test
                void handlesZeroMaxScore() {
                        MapDifficulty mdZero = MapDifficulty.builder()
                                        .id(UUID.randomUUID())
                                        .map(map)
                                        .difficulty(Difficulty.EXPERT_PLUS)
                                        .characteristic("Standard")
                                        .maxScore(0)
                                        .build();

                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mdZero)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        Score score = Score.builder()
                                        .id(UUID.randomUUID())
                                        .user(creator)
                                        .mapDifficulty(mdZero)
                                        .score(950000)
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(cm));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of());
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(creator.getId()),
                                        anyList()))
                                        .thenReturn(List.of(score));

                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getMaps().get(0).getUserAccuracy()).isNull();
                        assertThat(result.getMaps().get(0).isCompleted()).isFalse();
                }

                @Test
                void prerequisiteUnlockLogic() {
                        CampaignMap mapA = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        MapDifficulty mdB = MapDifficulty.builder()
                                        .id(UUID.randomUUID())
                                        .map(map)
                                        .difficulty(Difficulty.EXPERT_PLUS)
                                        .characteristic("Standard")
                                        .maxScore(1000000)
                                        .build();

                        CampaignMap mapB = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mdB)
                                        .accuracyRequirement(new BigDecimal("0.95"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        CampaignMapPath path = CampaignMapPath.builder()
                                        .id(UUID.randomUUID())
                                        .campaignMap(mapB)
                                        .comesFromCampaignMap(mapA)
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(mapA, mapB));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(path));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(creator.getId()),
                                        anyList()))
                                        .thenReturn(List.of());

                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getMaps().get(0).isUnlocked()).isTrue();
                        assertThat(result.getMaps().get(1).isUnlocked()).isFalse();
                }

                @Test
                void prerequisiteUnlocksWhenCompleted() {
                        CampaignMap mapA = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        MapDifficulty mdB = MapDifficulty.builder()
                                        .id(UUID.randomUUID())
                                        .map(map)
                                        .difficulty(Difficulty.EXPERT_PLUS)
                                        .characteristic("Standard")
                                        .maxScore(1000000)
                                        .build();

                        CampaignMap mapB = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mdB)
                                        .accuracyRequirement(new BigDecimal("0.95"))
                                        .xp(BigDecimal.TEN)
                                        .build();

                        CampaignMapPath path = CampaignMapPath.builder()
                                        .id(UUID.randomUUID())
                                        .campaignMap(mapB)
                                        .comesFromCampaignMap(mapA)
                                        .build();

                        Score scoreA = Score.builder()
                                        .id(UUID.randomUUID())
                                        .user(creator)
                                        .mapDifficulty(mapDifficulty)
                                        .score(950000) // 0.95 >= 0.90
                                        .build();

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignMapRepository.findByCampaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(mapA, mapB));
                        when(campaignMapPathRepository.findByCampaignMap_Campaign_IdAndActiveTrue(campaign.getId()))
                                        .thenReturn(List.of(path));
                        when(scoreRepository.findByUser_IdAndMapDifficulty_IdInAndActiveTrue(eq(creator.getId()),
                                        anyList()))
                                        .thenReturn(List.of(scoreA));

                        CampaignProgressResponse result = campaignService.getUserProgress(creator.getId(),
                                        campaign.getId());

                        assertThat(result.getMaps().get(0).isUnlocked()).isTrue();
                        assertThat(result.getMaps().get(0).isCompleted()).isTrue();
                        assertThat(result.getMaps().get(1).isUnlocked()).isTrue();
                        assertThat(result.getMaps().get(1).isCompleted()).isFalse();
                }
        }

        @Nested
        class CreateCampaign {

                @Test
                void createsAndReturnsCampaign() {
                        CreateCampaignRequest request = new CreateCampaignRequest();
                        request.setCreatorId(creator.getId());
                        request.setName("New Campaign");
                        request.setDescription("Desc");
                        request.setDifficulty("Medium");

                        when(userRepository.findById(creator.getId())).thenReturn(Optional.of(creator));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
                                Campaign c = inv.getArgument(0);
                                c.setId(UUID.randomUUID());
                                c.setCampaignMaps(List.of());
                                return c;
                        });

                        CampaignResponse result = campaignService.createCampaign(request);

                        assertThat(result.getName()).isEqualTo("New Campaign");
                        assertThat(result.getCreatorId()).isEqualTo(creator.getId());
                        verify(campaignRepository).save(any(Campaign.class));
                }

                @Test
                void throwsWhenCreatorNotFound() {
                        CreateCampaignRequest request = new CreateCampaignRequest();
                        request.setCreatorId(99999L);
                        request.setName("Campaign");

                        when(userRepository.findById(99999L)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> campaignService.createCampaign(request))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class AddCampaignMap {

                @Test
                void addsCampaignMapWithoutPrerequisites() {
                        AddCampaignMapRequest request = new AddCampaignMapRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setAccuracyRequirement(new BigDecimal("0.95"));
                        request.setXp(new BigDecimal("100"));

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignMapRepository.save(any(CampaignMap.class))).thenAnswer(inv -> {
                                CampaignMap cm = inv.getArgument(0);
                                cm.setId(UUID.randomUUID());
                                return cm;
                        });

                        CampaignMapResponse result = campaignService.addCampaignMap(campaign.getId(), request);

                        assertThat(result.getSongName()).isEqualTo("Test Song");
                        assertThat(result.getAccuracyRequirement()).isEqualByComparingTo(new BigDecimal("0.95"));
                        assertThat(result.getPrerequisiteMapIds()).isEmpty();
                }

                @Test
                void throwsWhenPrerequisiteBelongsToDifferentCampaign() {
                        UUID prereqId = UUID.randomUUID();
                        Campaign otherCampaign = Campaign.builder().id(UUID.randomUUID()).build();
                        CampaignMap prereqFromOtherCampaign = CampaignMap.builder()
                                        .id(prereqId)
                                        .campaign(otherCampaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .active(true)
                                        .build();

                        AddCampaignMapRequest request = new AddCampaignMapRequest();
                        request.setMapDifficultyId(mapDifficulty.getId());
                        request.setAccuracyRequirement(new BigDecimal("0.95"));
                        request.setPrerequisiteCampaignMapIds(List.of(prereqId));

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mapDifficulty.getId()))
                                        .thenReturn(Optional.of(mapDifficulty));
                        when(campaignMapRepository.save(any(CampaignMap.class))).thenAnswer(inv -> {
                                CampaignMap cm = inv.getArgument(0);
                                cm.setId(UUID.randomUUID());
                                return cm;
                        });
                        when(campaignMapRepository.findByIdAndActiveTrue(prereqId))
                                        .thenReturn(Optional.of(prereqFromOtherCampaign));

                        assertThatThrownBy(() -> campaignService.addCampaignMap(campaign.getId(), request))
                                        .isInstanceOf(ValidationException.class);
                }
        }

        @Nested
        class UpdateCampaign {

                @Test
                void updatesOnlyProvidedFields() {
                        UpdateCampaignRequest request = new UpdateCampaignRequest();
                        request.setName("Updated Name");
                        request.setVerified(true);

                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> {
                                Campaign c = inv.getArgument(0);
                                c.setCampaignMaps(List.of());
                                return c;
                        });

                        CampaignResponse result = campaignService.updateCampaign(campaign.getId(), request);

                        assertThat(result.getName()).isEqualTo("Updated Name");
                        assertThat(result.isVerified()).isTrue();
                        assertThat(result.getDescription()).isEqualTo("A test campaign");
                }
        }

        @Nested
        class DeactivateCampaign {

                @Test
                void deactivatesCampaign() {
                        when(campaignRepository.findByIdAndActiveTrue(campaign.getId()))
                                        .thenReturn(Optional.of(campaign));
                        when(campaignRepository.save(any(Campaign.class))).thenReturn(campaign);

                        campaignService.deactivateCampaign(campaign.getId());

                        assertThat(campaign.isActive()).isFalse();
                        verify(campaignRepository).save(campaign);
                }

                @Test
                void throwsWhenCampaignNotFound() {
                        UUID id = UUID.randomUUID();
                        when(campaignRepository.findByIdAndActiveTrue(id)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> campaignService.deactivateCampaign(id))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }

        @Nested
        class RemoveCampaignMap {

                @Test
                void deactivatesCampaignMap() {
                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(campaign)
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .active(true)
                                        .build();

                        when(campaignMapRepository.findByIdAndActiveTrue(cm.getId())).thenReturn(Optional.of(cm));
                        when(campaignMapRepository.save(any(CampaignMap.class))).thenReturn(cm);

                        campaignService.removeCampaignMap(campaign.getId(), cm.getId());

                        assertThat(cm.isActive()).isFalse();
                        verify(campaignMapRepository).save(cm);
                }

                @Test
                void throwsWhenMapBelongsToDifferentCampaign() {
                        CampaignMap cm = CampaignMap.builder()
                                        .id(UUID.randomUUID())
                                        .campaign(Campaign.builder().id(UUID.randomUUID()).build())
                                        .mapDifficulty(mapDifficulty)
                                        .accuracyRequirement(new BigDecimal("0.90"))
                                        .active(true)
                                        .build();

                        when(campaignMapRepository.findByIdAndActiveTrue(cm.getId())).thenReturn(Optional.of(cm));

                        assertThatThrownBy(() -> campaignService.removeCampaignMap(campaign.getId(), cm.getId()))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }
        }
}
