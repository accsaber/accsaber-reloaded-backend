package com.accsaber.backend.service.campaign;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.campaign.AddCampaignMapRequest;
import com.accsaber.backend.model.dto.request.campaign.CreateCampaignRequest;
import com.accsaber.backend.model.dto.request.campaign.UpdateCampaignRequest;
import com.accsaber.backend.model.dto.response.campaign.CampaignDetailResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignMapProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignMapResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignProgressResponse;
import com.accsaber.backend.model.dto.response.campaign.CampaignResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignMap;
import com.accsaber.backend.model.entity.campaign.CampaignMapPath;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.campaign.CampaignMapPathRepository;
import com.accsaber.backend.repository.campaign.CampaignMapRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignService {

        private final CampaignRepository campaignRepository;
        private final CampaignMapRepository campaignMapRepository;
        private final CampaignMapPathRepository campaignMapPathRepository;
        private final ScoreRepository scoreRepository;
        private final UserRepository userRepository;
        private final MapDifficultyRepository mapDifficultyRepository;

        public Page<CampaignResponse> findAllActiveCampaigns(Pageable pageable) {
                return campaignRepository.findByActiveTrue(pageable)
                                .map(this::toCampaignResponse);
        }

        public CampaignDetailResponse findCampaignById(UUID campaignId) {
                Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

                List<CampaignMap> maps = campaignMapRepository.findByCampaign_IdAndActiveTrue(campaignId);
                List<CampaignMapPath> allPaths = campaignMapPathRepository
                                .findByCampaignMap_Campaign_IdAndActiveTrue(campaignId);

                Map<UUID, List<UUID>> prerequisitesByMapId = allPaths.stream()
                                .collect(Collectors.groupingBy(
                                                p -> p.getCampaignMap().getId(),
                                                Collectors.mapping(p -> p.getComesFromCampaignMap().getId(),
                                                                Collectors.toList())));

                List<CampaignMapResponse> mapResponses = maps.stream()
                                .map(cm -> toCampaignMapResponse(cm,
                                                prerequisitesByMapId.getOrDefault(cm.getId(), List.of())))
                                .toList();

                return CampaignDetailResponse.builder()
                                .id(campaign.getId())
                                .creatorId(campaign.getCreator().getId())
                                .creatorName(campaign.getCreator().getName())
                                .name(campaign.getName())
                                .description(campaign.getDescription())
                                .difficulty(campaign.getDifficulty())
                                .verified(campaign.isVerified())
                                .mapCount(maps.size())
                                .createdAt(campaign.getCreatedAt())
                                .maps(mapResponses)
                                .build();
        }

        public CampaignProgressResponse getUserProgress(Long steamId, UUID campaignId) {
                Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

                List<CampaignMap> maps = campaignMapRepository.findByCampaign_IdAndActiveTrue(campaignId);
                List<CampaignMapPath> allPaths = campaignMapPathRepository
                                .findByCampaignMap_Campaign_IdAndActiveTrue(campaignId);

                Map<UUID, List<UUID>> prerequisitesByMapId = allPaths.stream()
                                .collect(Collectors.groupingBy(
                                                p -> p.getCampaignMap().getId(),
                                                Collectors.mapping(p -> p.getComesFromCampaignMap().getId(),
                                                                Collectors.toList())));

                List<UUID> mapDifficultyIds = maps.stream()
                                .map(cm -> cm.getMapDifficulty().getId())
                                .toList();
                Map<UUID, Score> scoreByMapDifficultyId = scoreRepository
                                .findByUser_IdAndMapDifficulty_IdInAndActiveTrue(steamId, mapDifficultyIds)
                                .stream()
                                .collect(Collectors.toMap(s -> s.getMapDifficulty().getId(), s -> s));

                Map<UUID, BigDecimal> accuracyByMapId = new HashMap<>();
                Map<UUID, Integer> scoreByMapId = new HashMap<>();
                Set<UUID> completedMapIds = new HashSet<>();

                for (CampaignMap cm : maps) {
                        Score score = scoreByMapDifficultyId.get(cm.getMapDifficulty().getId());
                        if (score != null) {
                                BigDecimal accuracy = computeAccuracy(score, cm.getMapDifficulty());
                                accuracyByMapId.put(cm.getId(), accuracy);
                                scoreByMapId.put(cm.getId(), score.getScore());

                                if (accuracy != null && accuracy.compareTo(cm.getAccuracyRequirement()) >= 0) {
                                        completedMapIds.add(cm.getId());
                                }
                        }
                }

                List<CampaignMapProgressResponse> progressMaps = new ArrayList<>();
                for (CampaignMap cm : maps) {
                        List<UUID> prereqs = prerequisitesByMapId.getOrDefault(cm.getId(), List.of());
                        boolean unlocked = prereqs.isEmpty() || completedMapIds.containsAll(prereqs);

                        MapDifficulty md = cm.getMapDifficulty();
                        progressMaps.add(CampaignMapProgressResponse.builder()
                                        .campaignMapId(cm.getId())
                                        .mapDifficultyId(md.getId())
                                        .songName(md.getMap().getSongName())
                                        .difficulty(md.getDifficulty().name())
                                        .characteristic(md.getCharacteristic())
                                        .accuracyRequirement(cm.getAccuracyRequirement())
                                        .userAccuracy(accuracyByMapId.get(cm.getId()))
                                        .userScore(scoreByMapId.get(cm.getId()))
                                        .completed(completedMapIds.contains(cm.getId()))
                                        .unlocked(unlocked)
                                        .build());
                }

                return CampaignProgressResponse.builder()
                                .campaignId(campaign.getId())
                                .campaignName(campaign.getName())
                                .totalMaps(maps.size())
                                .completedMaps(completedMapIds.size())
                                .maps(progressMaps)
                                .build();
        }

        @Transactional
        public CampaignResponse createCampaign(CreateCampaignRequest request) {
                User creator = userRepository.findById(request.getCreatorId())
                                .orElseThrow(() -> new ResourceNotFoundException("User", request.getCreatorId()));

                Campaign campaign = Campaign.builder()
                                .creator(creator)
                                .name(request.getName())
                                .description(request.getDescription())
                                .difficulty(request.getDifficulty())
                                .build();

                return toCampaignResponse(campaignRepository.save(campaign));
        }

        @Transactional
        public CampaignMapResponse addCampaignMap(UUID campaignId, AddCampaignMapRequest request) {
                Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

                MapDifficulty mapDifficulty = mapDifficultyRepository
                                .findByIdAndActiveTrue(request.getMapDifficultyId())
                                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty",
                                                request.getMapDifficultyId()));

                CampaignMap campaignMap = CampaignMap.builder()
                                .campaign(campaign)
                                .mapDifficulty(mapDifficulty)
                                .accuracyRequirement(request.getAccuracyRequirement())
                                .xp(request.getXp() != null ? request.getXp() : BigDecimal.ZERO)
                                .build();

                campaignMap = campaignMapRepository.save(campaignMap);

                List<UUID> prerequisiteIds = createPrerequisitePaths(campaignMap,
                                request.getPrerequisiteCampaignMapIds());

                return toCampaignMapResponse(campaignMap, prerequisiteIds);
        }

        @Transactional
        public CampaignResponse updateCampaign(UUID campaignId, UpdateCampaignRequest request) {
                Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

                if (request.getName() != null) {
                        campaign.setName(request.getName());
                }
                if (request.getDescription() != null) {
                        campaign.setDescription(request.getDescription());
                }
                if (request.getDifficulty() != null) {
                        campaign.setDifficulty(request.getDifficulty());
                }
                if (request.getVerified() != null) {
                        campaign.setVerified(request.getVerified());
                }

                return toCampaignResponse(campaignRepository.save(campaign));
        }

        @Transactional
        public void deactivateCampaign(UUID campaignId) {
                Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
                campaign.setActive(false);
                campaignRepository.save(campaign);
        }

        @Transactional
        public void removeCampaignMap(UUID campaignId, UUID campaignMapId) {
                CampaignMap campaignMap = campaignMapRepository.findByIdAndActiveTrue(campaignMapId)
                                .filter(cm -> cm.getCampaign().getId().equals(campaignId))
                                .orElseThrow(() -> new ResourceNotFoundException("CampaignMap", campaignMapId));
                campaignMap.setActive(false);
                campaignMapRepository.save(campaignMap);
        }

        private BigDecimal computeAccuracy(Score score, MapDifficulty mapDifficulty) {
                if (mapDifficulty.getMaxScore() == null || mapDifficulty.getMaxScore() == 0) {
                        return null;
                }
                return BigDecimal.valueOf(score.getScore())
                                .divide(BigDecimal.valueOf(mapDifficulty.getMaxScore()), 6, RoundingMode.HALF_UP);
        }

        private List<UUID> createPrerequisitePaths(CampaignMap campaignMap, List<UUID> prerequisiteIds) {
                if (prerequisiteIds == null || prerequisiteIds.isEmpty()) {
                        return List.of();
                }

                List<UUID> savedIds = new ArrayList<>();
                for (UUID prereqId : prerequisiteIds) {
                        CampaignMap prerequisite = campaignMapRepository.findByIdAndActiveTrue(prereqId)
                                        .orElseThrow(() -> new ResourceNotFoundException("CampaignMap (prerequisite)",
                                                        prereqId));

                        if (!prerequisite.getCampaign().getId().equals(campaignMap.getCampaign().getId())) {
                                throw new ValidationException(
                                                "Prerequisite map must belong to the same campaign");
                        }

                        CampaignMapPath path = CampaignMapPath.builder()
                                        .campaignMap(campaignMap)
                                        .comesFromCampaignMap(prerequisite)
                                        .build();
                        campaignMapPathRepository.save(path);
                        savedIds.add(prereqId);
                }
                return savedIds;
        }

        private CampaignResponse toCampaignResponse(Campaign campaign) {
                int mapCount = campaign.getCampaignMaps() != null
                                ? (int) campaign.getCampaignMaps().stream().filter(CampaignMap::isActive).count()
                                : 0;
                return CampaignResponse.builder()
                                .id(campaign.getId())
                                .creatorId(campaign.getCreator().getId())
                                .creatorName(campaign.getCreator().getName())
                                .name(campaign.getName())
                                .description(campaign.getDescription())
                                .difficulty(campaign.getDifficulty())
                                .verified(campaign.isVerified())
                                .mapCount(mapCount)
                                .createdAt(campaign.getCreatedAt())
                                .build();
        }

        private CampaignMapResponse toCampaignMapResponse(CampaignMap cm, List<UUID> prerequisiteMapIds) {
                MapDifficulty md = cm.getMapDifficulty();
                return CampaignMapResponse.builder()
                                .id(cm.getId())
                                .mapDifficultyId(md.getId())
                                .songName(md.getMap().getSongName())
                                .songAuthor(md.getMap().getSongAuthor())
                                .mapAuthor(md.getMap().getMapAuthor())
                                .coverUrl(md.getMap().getCoverUrl())
                                .difficulty(md.getDifficulty().name())
                                .characteristic(md.getCharacteristic())
                                .accuracyRequirement(cm.getAccuracyRequirement())
                                .xp(cm.getXp())
                                .prerequisiteMapIds(prerequisiteMapIds)
                                .build();
        }
}
