package com.accsaber.backend.service.og;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapDifficultyStatisticsRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.milestone.LevelService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OgService {

    private static final String BASE_URL = "https://accsaberreloaded.com";
    private static final String SITE_NAME = "AccSaber Reloaded";

    private final UserRepository userRepository;
    private final UserCategoryStatisticsRepository statisticsRepository;
    private final MapRepository mapRepository;
    private final MapDifficultyRepository difficultyRepository;
    private final MapDifficultyComplexityRepository complexityRepository;
    private final MapDifficultyStatisticsRepository diffStatsRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final LevelService levelService;

    public String buildPlayerOg(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null) {
            return buildDefaultHtml(BASE_URL + "/players/" + userId);
        }

        String url = BASE_URL + "/players/" + userId;
        String image = user.getAvatarUrl();
        String title = user.getName() + " | " + SITE_NAME;
        String description = user.getName();

        UserCategoryStatistics stats = statisticsRepository
                .findByUser_IdAndCategory_CodeAndActiveTrue(userId, "overall").orElse(null);

        if (stats != null) {
            title = user.getName() + " - #" + stats.getRanking() + " | " + SITE_NAME;

            StringBuilder desc = new StringBuilder();
            desc.append("#").append(stats.getRanking()).append(" Global");
            if (user.getCountry() != null && stats.getCountryRanking() != null) {
                desc.append(" / ").append(user.getCountry().toUpperCase())
                        .append(" #").append(stats.getCountryRanking());
            }
            desc.append("\nAccSaber Points: ")
                    .append(stats.getAp().setScale(2, RoundingMode.HALF_UP)).append(" AP");
            if (stats.getAverageAcc() != null) {
                BigDecimal acc = stats.getAverageAcc().multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                desc.append("\nAverage Accuracy: ").append(acc).append("%");
            }
            desc.append("\nRanked Plays: ").append(stats.getRankedPlays());

            LevelResponse level = levelService.calculateLevel(user.getTotalXp());
            if (level != null) {
                desc.append("\nLevel: ").append(level.getLevel());
                if (level.getTitle() != null && !level.getTitle().isBlank()) {
                    desc.append(" (").append(level.getTitle()).append(")");
                }
            }

            description = desc.toString();
        }

        return buildHtml("profile", title, description, image, url);
    }

    public String buildMapOg(String mapIdOrCode, UUID legacyDifficultyId,
            Difficulty difficulty, String characteristic) {
        Map map = resolveMap(mapIdOrCode);
        if (map == null) {
            return buildDefaultHtml(BASE_URL + "/maps/" + mapIdOrCode);
        }

        String canonical = map.getBeatsaverCode() != null ? map.getBeatsaverCode() : map.getId().toString();
        String url = BASE_URL + "/maps/" + canonical;
        String image = map.getCdnCoverUrl() != null ? map.getCdnCoverUrl() : map.getCoverUrl();

        MapDifficulty diff = resolveDifficulty(map, legacyDifficultyId, difficulty, characteristic);

        String title = map.getSongAuthor() + " - " + map.getSongName() + " | " + SITE_NAME;
        StringBuilder desc = new StringBuilder("Mapped by ").append(map.getMapAuthor());

        if (diff != null) {
            title = map.getSongAuthor() + " - " + map.getSongName()
                    + " [" + diff.getDifficulty().getDbValue() + "] | " + SITE_NAME;

            desc.append("\n").append(diff.getDifficulty().getDbValue())
                    .append(" \u00b7 ").append(diff.getCategory().getName());

            complexityRepository.findByMapDifficultyIdAndActiveTrue(diff.getId())
                    .ifPresent(c -> desc.append("\nComplexity: ")
                            .append(c.getComplexity().setScale(2, RoundingMode.HALF_UP)));

            diffStatsRepository.findByMapDifficultyIdAndActiveTrue(diff.getId())
                    .ifPresent(s -> {
                        BigDecimal max = s.getMaxAp() != null
                                ? s.getMaxAp().setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                        BigDecimal avg = s.getAverageAp() != null
                                ? s.getAverageAp().setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                        desc.append("\nMax/Avg AP: ").append(max).append(" / ").append(avg);
                        desc.append("\n").append(s.getTotalScores()).append(" scores");
                    });
        }

        return buildHtml("website", title, desc.toString(), image, url);
    }

    public String buildCampaignOg(String campaignIdOrSlug) {
        Campaign campaign = resolveCampaign(campaignIdOrSlug);
        if (campaign == null || campaign.getStatus() == CampaignStatus.DRAFT) {
            return buildDefaultHtml(BASE_URL + "/campaigns/" + campaignIdOrSlug);
        }

        String url = BASE_URL + "/campaigns/" + campaign.getSlug();
        String image = campaign.getIconUrl() != null ? campaign.getIconUrl() : campaign.getBackgroundUrl();
        String title = campaign.getName() + " | " + SITE_NAME;

        StringBuilder desc = new StringBuilder();
        String creatorName = campaign.getCreatorAlias() != null && !campaign.getCreatorAlias().isBlank()
                ? campaign.getCreatorAlias()
                : campaign.getCreator() != null ? campaign.getCreator().getName() : null;
        if (creatorName != null) {
            desc.append("Created by ").append(creatorName);
        }
        if (campaign.getSummary() != null && !campaign.getSummary().isBlank()) {
            if (!desc.isEmpty()) {
                desc.append("\n");
            }
            desc.append(campaign.getSummary());
        }

        long mapCount = campaignDifficultyRepository
                .countByCampaign_IdAndBarrierFalseAndActiveTrue(campaign.getId());
        if (!desc.isEmpty()) {
            desc.append("\n");
        }
        desc.append(mapCount).append(mapCount == 1 ? " map" : " maps");
        if (campaign.getStatus() == CampaignStatus.CURATED) {
            desc.append(" · Curated");
        }
        if (campaign.isOfficial()) {
            desc.append(" · Official");
        }
        desc.append("\n▲ ").append(campaign.getTotalUpvotes())
                .append(" / ▼ ").append(campaign.getTotalDownvotes());

        return buildHtml("website", title, desc.toString(), image, url);
    }

    private Campaign resolveCampaign(String campaignIdOrSlug) {
        if (campaignIdOrSlug == null || campaignIdOrSlug.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(campaignIdOrSlug);
            Campaign byId = campaignRepository.findByIdAndActiveTrue(id).orElse(null);
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return campaignRepository.findBySlugAndActiveTrue(campaignIdOrSlug).orElse(null);
    }

    private Map resolveMap(String mapIdOrCode) {
        if (mapIdOrCode == null || mapIdOrCode.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(mapIdOrCode);
            Map byId = mapRepository.findByIdAndActiveTrue(id).orElse(null);
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }
        List<Map> byCode = mapRepository.findActiveByBeatsaverCodeLatestFirst(
                mapIdOrCode, PageRequest.of(0, 1));
        return byCode.isEmpty() ? null : byCode.get(0);
    }

    private MapDifficulty resolveDifficulty(Map map, UUID legacyDifficultyId,
            Difficulty difficulty, String characteristic) {
        if (legacyDifficultyId != null) {
            MapDifficulty d = difficultyRepository.findByIdAndActiveTrue(legacyDifficultyId).orElse(null);
            if (d != null && d.getMap().getId().equals(map.getId())) {
                return d;
            }
        }
        List<MapDifficulty> all = difficultyRepository.findByMapIdAndActiveTrue(map.getId());
        if (difficulty != null) {
            List<MapDifficulty> filtered = all.stream()
                    .filter(d -> d.getDifficulty() == difficulty)
                    .toList();
            if (characteristic != null && !characteristic.isBlank()) {
                filtered = filtered.stream()
                        .filter(d -> characteristic.equalsIgnoreCase(d.getCharacteristic()))
                        .toList();
            }
            if (!filtered.isEmpty()) {
                return filtered.stream()
                        .filter(d -> d.getStatus() == MapDifficultyStatus.RANKED)
                        .findFirst()
                        .orElse(filtered.get(0));
            }
        }
        return all.stream()
                .filter(d -> d.getStatus() == MapDifficultyStatus.RANKED)
                .findFirst()
                .orElse(all.isEmpty() ? null : all.get(0));
    }

    private String buildDefaultHtml(String url) {
        return buildHtml("website", SITE_NAME, "Accuracy-based Beat Saber leaderboards", null, url);
    }

    private String buildHtml(String type, String title, String description, String image, String url) {
        String safeTitle = escapeHtml(title);
        String safeDesc = escapeHtml(description);
        StringBuilder sb = new StringBuilder("<!DOCTYPE html><html><head>");
        sb.append("<meta property=\"og:type\" content=\"").append(type).append("\"/>");
        sb.append("<meta property=\"og:site_name\" content=\"").append(SITE_NAME).append("\"/>");
        sb.append("<meta property=\"og:title\" content=\"").append(safeTitle).append("\"/>");
        sb.append("<meta property=\"og:description\" content=\"").append(safeDesc).append("\"/>");
        if (image != null) {
            sb.append("<meta property=\"og:image\" content=\"").append(escapeHtml(image)).append("\"/>");
        }
        sb.append("<meta property=\"og:url\" content=\"").append(url).append("\"/>");
        sb.append("<meta name=\"twitter:card\" content=\"summary\"/>");
        sb.append("<meta name=\"twitter:title\" content=\"").append(safeTitle).append("\"/>");
        sb.append("<meta name=\"twitter:description\" content=\"").append(safeDesc).append("\"/>");
        if (image != null) {
            sb.append("<meta name=\"twitter:image\" content=\"").append(escapeHtml(image)).append("\"/>");
        }
        sb.append("</head><body></body></html>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
