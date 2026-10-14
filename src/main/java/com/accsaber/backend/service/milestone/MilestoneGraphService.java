package com.accsaber.backend.service.milestone;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetGroupRequest;
import com.accsaber.backend.model.dto.request.milestone.CreateMilestoneSetLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.CreatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdateMilestoneSetLinkRequest;
import com.accsaber.backend.model.dto.request.milestone.UpdatePrerequisiteLinkRequest;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetGroupResponse;
import com.accsaber.backend.model.dto.response.milestone.MilestoneSetLinkResponse;
import com.accsaber.backend.model.dto.response.milestone.PrerequisiteLinkResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyMilestoneLink;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.MilestonePrerequisiteLink;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.milestone.MilestoneSetGroup;
import com.accsaber.backend.model.entity.milestone.MilestoneSetLink;
import com.accsaber.backend.repository.map.MapDifficultyMilestoneLinkRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.milestone.MilestonePrerequisiteLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetGroupRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetLinkRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MilestoneGraphService {

    private final MilestoneRepository milestoneRepository;
    private final MilestoneSetRepository milestoneSetRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final MapDifficultyMilestoneLinkRepository mapDifficultyMilestoneLinkRepository;
    private final MilestonePrerequisiteLinkRepository prerequisiteLinkRepository;
    private final MilestoneSetGroupRepository setGroupRepository;
    private final MilestoneSetLinkRepository setLinkRepository;

    @Transactional
    public void addMapDifficultyLinks(UUID milestoneId, List<UUID> mapDifficultyIds) {
        createMapDifficultyLinks(requireActiveMilestone(milestoneId), mapDifficultyIds);
    }

    @Transactional
    public void removeMapDifficultyLinks(UUID milestoneId, List<UUID> mapDifficultyIds) {
        requireActiveMilestone(milestoneId);
        mapDifficultyMilestoneLinkRepository.deleteByMilestone_IdAndMapDifficulty_IdIn(milestoneId, mapDifficultyIds);
    }

    @Transactional
    public void createMapDifficultyLinks(Milestone milestone, List<UUID> mapDifficultyIds) {
        if (mapDifficultyIds == null || mapDifficultyIds.isEmpty()) {
            return;
        }
        for (UUID mdId : mapDifficultyIds) {
            if (mapDifficultyMilestoneLinkRepository.existsByMapDifficulty_IdAndMilestone_Id(mdId, milestone.getId())) {
                continue;
            }
            MapDifficulty md = mapDifficultyRepository.findByIdAndActiveTrue(mdId)
                    .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mdId));
            MapDifficultyMilestoneLink link = MapDifficultyMilestoneLink.builder()
                    .milestone(milestone)
                    .mapDifficulty(md)
                    .build();
            mapDifficultyMilestoneLinkRepository.save(link);
        }
    }

    @Transactional
    public PrerequisiteLinkResponse createPrerequisiteLink(CreatePrerequisiteLinkRequest request) {
        Milestone milestone = requireActiveMilestone(request.getMilestoneId());
        Milestone prerequisite = requireActiveMilestone(request.getPrerequisiteMilestoneId());
        if (prerequisiteLinkRepository.existsByMilestone_IdAndPrerequisiteMilestone_IdAndActiveTrue(
                milestone.getId(), prerequisite.getId())) {
            throw new ConflictException("Prerequisite link already exists");
        }
        MilestonePrerequisiteLink link = MilestonePrerequisiteLink.builder()
                .milestone(milestone)
                .prerequisiteMilestone(prerequisite)
                .blocker(request.isBlocker())
                .build();
        return toPrerequisiteLinkResponse(prerequisiteLinkRepository.save(link));
    }

    @Transactional
    public PrerequisiteLinkResponse updatePrerequisiteLink(UUID linkId, UpdatePrerequisiteLinkRequest request) {
        MilestonePrerequisiteLink link = prerequisiteLinkRepository.findById(linkId)
                .filter(MilestonePrerequisiteLink::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("PrerequisiteLink", linkId));
        link.setBlocker(request.getBlocker());
        return toPrerequisiteLinkResponse(prerequisiteLinkRepository.save(link));
    }

    @Transactional
    public void deactivatePrerequisiteLink(UUID linkId) {
        MilestonePrerequisiteLink link = prerequisiteLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("PrerequisiteLink", linkId));
        link.setActive(false);
        prerequisiteLinkRepository.save(link);
    }

    public List<PrerequisiteLinkResponse> findPrerequisitesByMilestone(UUID milestoneId) {
        return prerequisiteLinkRepository.findByMilestone_IdAndActiveTrue(milestoneId).stream()
                .map(this::toPrerequisiteLinkResponse)
                .toList();
    }

    public List<PrerequisiteLinkResponse> findAllActivePrerequisiteLinks() {
        return prerequisiteLinkRepository.findAllActiveWithPrerequisites().stream()
                .map(this::toPrerequisiteLinkResponse)
                .toList();
    }

    public List<MilestoneSetGroupResponse> findAllActiveGroups() {
        Map<UUID, List<MilestoneSetLinkResponse>> linksByGroup = setLinkRepository.findAllActiveWithSets().stream()
                .map(this::toSetLinkResponse)
                .collect(Collectors.groupingBy(MilestoneSetLinkResponse::getGroupId));
        return setGroupRepository.findByActiveTrue().stream()
                .map(group -> toSetGroupResponse(group).toBuilder()
                        .links(linksByGroup.getOrDefault(group.getId(), List.of()))
                        .build())
                .toList();
    }

    @Transactional
    public MilestoneSetGroupResponse createSetGroup(CreateMilestoneSetGroupRequest request) {
        MilestoneSetGroup group = MilestoneSetGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        return toSetGroupResponse(setGroupRepository.save(group));
    }

    @Transactional
    public MilestoneSetGroupResponse updateSetGroup(UUID groupId, CreateMilestoneSetGroupRequest request) {
        MilestoneSetGroup group = setGroupRepository.findByIdAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetGroup", groupId));
        group.setName(request.getName());
        group.setDescription(request.getDescription());
        return toSetGroupResponse(setGroupRepository.save(group));
    }

    @Transactional
    public void deactivateSetGroup(UUID groupId) {
        MilestoneSetGroup group = setGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetGroup", groupId));
        group.setActive(false);
        setGroupRepository.save(group);
    }

    @Transactional
    public MilestoneSetLinkResponse createSetLink(CreateMilestoneSetLinkRequest request) {
        MilestoneSetGroup group = setGroupRepository.findByIdAndActiveTrue(request.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetGroup", request.getGroupId()));
        MilestoneSet set = milestoneSetRepository.findByIdAndActiveTrue(request.getSetId())
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", request.getSetId()));
        if (setLinkRepository.existsByGroup_IdAndMilestoneSet_IdAndActiveTrue(group.getId(), set.getId())) {
            throw new ConflictException("Set link already exists");
        }
        MilestoneSetLink link = MilestoneSetLink.builder()
                .group(group)
                .milestoneSet(set)
                .sortOrder(request.getSortOrder())
                .build();
        return toSetLinkResponse(setLinkRepository.save(link));
    }

    @Transactional
    public MilestoneSetLinkResponse updateSetLink(UUID linkId, UpdateMilestoneSetLinkRequest request) {
        MilestoneSetLink link = setLinkRepository.findById(linkId)
                .filter(MilestoneSetLink::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetLink", linkId));
        link.setSortOrder(request.getSortOrder());
        return toSetLinkResponse(setLinkRepository.save(link));
    }

    @Transactional
    public void deactivateSetLink(UUID linkId) {
        MilestoneSetLink link = setLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSetLink", linkId));
        link.setActive(false);
        setLinkRepository.save(link);
    }

    public List<MilestoneSetLinkResponse> findSetLinksBySet(UUID setId) {
        return setLinkRepository.findBySetIdWithGroup(setId).stream()
                .map(this::toSetLinkResponse)
                .toList();
    }

    private Milestone requireActiveMilestone(UUID id) {
        return milestoneRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
    }

    private PrerequisiteLinkResponse toPrerequisiteLinkResponse(MilestonePrerequisiteLink link) {
        Milestone prereq = link.getPrerequisiteMilestone();
        return PrerequisiteLinkResponse.builder()
                .id(link.getId())
                .milestoneId(link.getMilestone().getId())
                .prerequisiteMilestoneId(prereq.getId())
                .prerequisiteTitle(prereq.getTitle())
                .prerequisiteTier(prereq.getTier().name())
                .blocker(link.isBlocker())
                .createdAt(link.getCreatedAt())
                .build();
    }

    private MilestoneSetGroupResponse toSetGroupResponse(MilestoneSetGroup group) {
        return MilestoneSetGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .createdAt(group.getCreatedAt())
                .build();
    }

    private MilestoneSetLinkResponse toSetLinkResponse(MilestoneSetLink link) {
        return MilestoneSetLinkResponse.builder()
                .id(link.getId())
                .groupId(link.getGroup().getId())
                .groupName(link.getGroup().getName())
                .setId(link.getMilestoneSet().getId())
                .setTitle(link.getMilestoneSet().getTitle())
                .sortOrder(link.getSortOrder())
                .createdAt(link.getCreatedAt())
                .build();
    }
}
