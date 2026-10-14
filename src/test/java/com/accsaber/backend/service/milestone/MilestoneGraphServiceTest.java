package com.accsaber.backend.service.milestone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.MilestoneQuerySpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.FilterSpec;
import com.accsaber.backend.model.dto.MilestoneQuerySpec.SelectSpec;
import com.accsaber.backend.model.dto.request.milestone.CreatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyMilestoneLink;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestonePrerequisiteLink;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneTier;
import com.accsaber.backend.repository.map.MapDifficultyMilestoneLinkRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.milestone.MilestonePrerequisiteLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;

@ExtendWith(MockitoExtension.class)
class MilestoneGraphServiceTest {

        @Mock
        private MilestoneRepository milestoneRepository;
        @Mock
        private MapDifficultyRepository mapDifficultyRepository;
        @Mock
        private MapDifficultyMilestoneLinkRepository mapDifficultyMilestoneLinkRepository;
        @Mock
        private MilestonePrerequisiteLinkRepository prerequisiteLinkRepository;

        @InjectMocks
        private MilestoneGraphService service;

        private MilestoneSet set;
        private MilestoneQuerySpec querySpec;

        @BeforeEach
        void setUp() {
                set = MilestoneSet.builder()
                                .id(UUID.randomUUID())
                                .title("Map Milestones")
                                .description("Milestones tied to maps")
                                .setBonusXp(0.0)
                                .build();

                querySpec = new MilestoneQuerySpec(
                                new SelectSpec("MAX", "ap"),
                                "scores",
                                List.of(new FilterSpec("active", "=", true)));
        }

        private Milestone milestone(String title, MilestoneTier tier) {
                return Milestone.builder()
                                .id(UUID.randomUUID())
                                .milestoneSet(set)
                                .title(title)
                                .type("milestone")
                                .tier(tier)
                                .xp((double) (100))
                                .querySpec(querySpec)
                                .targetValue((double) (95))
                                .comparison("GTE")
                                .build();
        }

        @Nested
        class CreateMapDifficultyLinks {

                @Test
                void withMapDifficultyIds_linkContainsCorrectReferences() {
                        UUID mdId = UUID.randomUUID();
                        MapDifficulty md = MapDifficulty.builder().id(mdId).build();
                        Milestone saved = milestone("Map Milestone", MilestoneTier.gold);

                        when(mapDifficultyRepository.findByIdAndActiveTrue(mdId)).thenReturn(Optional.of(md));

                        ArgumentCaptor<MapDifficultyMilestoneLink> captor = ArgumentCaptor
                                        .forClass(MapDifficultyMilestoneLink.class);
                        when(mapDifficultyMilestoneLinkRepository.save(captor.capture()))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        service.createMapDifficultyLinks(saved, List.of(mdId));

                        MapDifficultyMilestoneLink link = captor.getValue();
                        assertThat(link.getMilestone()).isEqualTo(saved);
                        assertThat(link.getMapDifficulty()).isEqualTo(md);
                }

                @ParameterizedTest(name = "mapDifficultyIds = {0}")
                @NullAndEmptySource
                void missingMapDifficultyIds_skipsLinkCreation(List<UUID> mapDifficultyIds) {
                        service.createMapDifficultyLinks(milestone("No Maps", MilestoneTier.bronze), mapDifficultyIds);

                        verify(mapDifficultyMilestoneLinkRepository, never()).save(any());
                        verify(mapDifficultyRepository, never()).findByIdAndActiveTrue(any());
                }
        }

        @Nested
        class PrerequisiteLinks {

                private Milestone milestone;
                private Milestone prerequisite;

                @BeforeEach
                void setUpPrerequisite() {
                        milestone = milestone("AP Hero", MilestoneTier.gold);
                        prerequisite = milestone("Prerequisite", MilestoneTier.silver);
                }

                @Test
                void createLink_success() {
                        CreatePrerequisiteLinkRequest request = new CreatePrerequisiteLinkRequest();
                        request.setMilestoneId(milestone.getId());
                        request.setPrerequisiteMilestoneId(prerequisite.getId());
                        request.setBlocker(true);

                        when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                                        .thenReturn(Optional.of(milestone));
                        when(milestoneRepository.findByIdAndActiveTrue(prerequisite.getId()))
                                        .thenReturn(Optional.of(prerequisite));
                        when(prerequisiteLinkRepository.existsByMilestone_IdAndPrerequisiteMilestone_IdAndActiveTrue(
                                        milestone.getId(), prerequisite.getId())).thenReturn(false);
                        when(prerequisiteLinkRepository.save(any())).thenAnswer(i -> {
                                MilestonePrerequisiteLink link = i.getArgument(0);
                                link.setId(UUID.randomUUID());
                                return link;
                        });

                        PrerequisiteLinkResponse response = service.createPrerequisiteLink(request);

                        assertThat(response.getMilestoneId()).isEqualTo(milestone.getId());
                        assertThat(response.getPrerequisiteMilestoneId()).isEqualTo(prerequisite.getId());
                        assertThat(response.getPrerequisiteTitle()).isEqualTo("Prerequisite");
                        assertThat(response.isBlocker()).isTrue();
                }

                @Test
                void createLink_duplicateThrowsConflict() {
                        CreatePrerequisiteLinkRequest request = new CreatePrerequisiteLinkRequest();
                        request.setMilestoneId(milestone.getId());
                        request.setPrerequisiteMilestoneId(prerequisite.getId());

                        when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                                        .thenReturn(Optional.of(milestone));
                        when(milestoneRepository.findByIdAndActiveTrue(prerequisite.getId()))
                                        .thenReturn(Optional.of(prerequisite));
                        when(prerequisiteLinkRepository.existsByMilestone_IdAndPrerequisiteMilestone_IdAndActiveTrue(
                                        milestone.getId(), prerequisite.getId())).thenReturn(true);

                        assertThatThrownBy(() -> service.createPrerequisiteLink(request))
                                        .isInstanceOf(ConflictException.class);
                }
        }

        @Nested
        class AddMapDifficultyLinks {

                private Milestone milestone;

                @BeforeEach
                void setUpMilestone() {
                        milestone = Milestone.builder()
                                        .id(UUID.randomUUID())
                                        .milestoneSet(set)
                                        .title("Existing Milestone")
                                        .type("milestone")
                                        .tier(MilestoneTier.silver)
                                        .xp((double) (200))
                                        .querySpec(querySpec)
                                        .targetValue((double) (90))
                                        .comparison("GTE")
                                        .build();
                }

                @Test
                void addsLinksToExistingMilestone() {
                        UUID md1Id = UUID.randomUUID();
                        UUID md2Id = UUID.randomUUID();
                        MapDifficulty md1 = MapDifficulty.builder().id(md1Id).build();
                        MapDifficulty md2 = MapDifficulty.builder().id(md2Id).build();

                        when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                                        .thenReturn(Optional.of(milestone));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(md1Id)).thenReturn(Optional.of(md1));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(md2Id)).thenReturn(Optional.of(md2));
                        when(mapDifficultyMilestoneLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        service.addMapDifficultyLinks(milestone.getId(), List.of(md1Id, md2Id));

                        verify(mapDifficultyMilestoneLinkRepository, times(2))
                                        .save(any(MapDifficultyMilestoneLink.class));
                }

                @Test
                void linkReferencesCorrectMilestoneAndDifficulty() {
                        UUID mdId = UUID.randomUUID();
                        MapDifficulty md = MapDifficulty.builder().id(mdId).build();

                        when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                                        .thenReturn(Optional.of(milestone));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(mdId)).thenReturn(Optional.of(md));

                        ArgumentCaptor<MapDifficultyMilestoneLink> captor = ArgumentCaptor
                                        .forClass(MapDifficultyMilestoneLink.class);
                        when(mapDifficultyMilestoneLinkRepository.save(captor.capture()))
                                        .thenAnswer(inv -> inv.getArgument(0));

                        service.addMapDifficultyLinks(milestone.getId(), List.of(mdId));

                        MapDifficultyMilestoneLink link = captor.getValue();
                        assertThat(link.getMilestone()).isEqualTo(milestone);
                        assertThat(link.getMapDifficulty()).isEqualTo(md);
                }

                @Test
                void mapDifficultyNotFound_throwsResourceNotFoundException() {
                        UUID missingMdId = UUID.randomUUID();

                        when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                                        .thenReturn(Optional.of(milestone));
                        when(mapDifficultyRepository.findByIdAndActiveTrue(missingMdId))
                                        .thenReturn(Optional.empty());

                        assertThatThrownBy(() -> service.addMapDifficultyLinks(milestone.getId(), List.of(missingMdId)))
                                        .isInstanceOf(ResourceNotFoundException.class);
                }

                @Test
                void mixOfNewAndDuplicate_onlyCreatesNewLinks() {
                        UUID existingMdId = UUID.randomUUID();
                        UUID newMdId = UUID.randomUUID();
                        MapDifficulty newMd = MapDifficulty.builder().id(newMdId).build();

                        when(milestoneRepository.findByIdAndActiveTrue(milestone.getId()))
                                        .thenReturn(Optional.of(milestone));
                        when(mapDifficultyMilestoneLinkRepository
                                        .existsByMapDifficulty_IdAndMilestone_Id(existingMdId, milestone.getId()))
                                        .thenReturn(true);
                        when(mapDifficultyMilestoneLinkRepository
                                        .existsByMapDifficulty_IdAndMilestone_Id(newMdId, milestone.getId()))
                                        .thenReturn(false);
                        when(mapDifficultyRepository.findByIdAndActiveTrue(newMdId)).thenReturn(Optional.of(newMd));
                        when(mapDifficultyMilestoneLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                        service.addMapDifficultyLinks(milestone.getId(), List.of(existingMdId, newMdId));

                        verify(mapDifficultyMilestoneLinkRepository, times(1))
                                        .save(any(MapDifficultyMilestoneLink.class));
                        verify(mapDifficultyRepository, never()).findByIdAndActiveTrue(existingMdId);
                        verify(mapDifficultyRepository).findByIdAndActiveTrue(newMdId);
                }
        }
}
