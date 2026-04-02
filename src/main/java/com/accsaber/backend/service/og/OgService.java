package com.accsaber.backend.service.og;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.model.entity.map.Map;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserCategoryStatistics;
import com.accsaber.backend.repository.map.MapDifficultyComplexityRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.MapDifficultyStatisticsRepository;
import com.accsaber.backend.repository.map.MapRepository;
import com.accsaber.backend.repository.user.UserCategoryStatisticsRepository;
import com.accsaber.backend.repository.user.UserRepository;

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

    public String buildPlayerOg(Long userId) {
        User user = userRepository.findByIdAndActiveTrue(userId).orElse(null);
        if (user == null) {
            return buildDefaultHtml(BASE_URL + "/players/" + userId);
        }

        String title = user.getName() + " | " + SITE_NAME;
        String description = user.getName();
        String image = user.getAvatarUrl();
        String url = BASE_URL + "/players/" + userId;

        UserCategoryStatistics stats = statisticsRepository
                .findByUser_IdAndCategory_CodeAndActiveTrue(userId, "overall").orElse(null);
        if (stats != null) {
            title = user.getName() + " - #" + stats.getRanking() + " | " + SITE_NAME;
            BigDecimal acc = stats.getAverageAcc() != null
                    ? stats.getAverageAcc().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : null;
            description = user.getName() + " - " + stats.getAp().setScale(2, RoundingMode.HALF_UP) + " AP, #"
                    + stats.getRanking() + " Global, "
                    + (acc != null ? acc + "%" : "N/A") + " Avg Accuracy, "
                    + stats.getRankedPlays() + " Ranked Plays";
        }
        return buildHtml("profile", title, description, image, url);
    }

    public String buildMapOg(UUID mapId, UUID difficultyId) {
        Map map = mapRepository.findByIdAndActiveTrue(mapId).orElse(null);
        if (map == null) {
            return buildDefaultHtml(BASE_URL + "/maps/" + mapId);
        }

        String url = BASE_URL + "/maps/" + mapId;
        String image = map.getCoverUrl();

        MapDifficulty diff = null;
        if (difficultyId != null) {
            diff = difficultyRepository.findByIdAndActiveTrue(difficultyId).orElse(null);
        }
        if (diff == null) {
            diff = difficultyRepository.findByMapIdAndActiveTrue(mapId).stream()
                    .filter(d -> d.getStatus() == MapDifficultyStatus.RANKED)
                    .findFirst().orElse(null);
        }

        String title = map.getSongAuthor() + " - " + map.getSongName() + " | " + SITE_NAME;
        String description = "Mapped by " + map.getMapAuthor();

        if (diff != null) {
            title = map.getSongAuthor() + " - " + map.getSongName()
                    + " [" + diff.getDifficulty().getDbValue() + "] | " + SITE_NAME;

            StringBuilder desc = new StringBuilder("Mapped by " + map.getMapAuthor());
            desc.append(" · ").append(diff.getCategory().getName());

            complexityRepository.findByMapDifficultyIdAndActiveTrue(diff.getId())
                    .ifPresent(c -> desc.append(" · Complexity ")
                            .append(c.getComplexity().setScale(2, RoundingMode.HALF_UP)));

            diffStatsRepository.findByMapDifficultyIdAndActiveTrue(diff.getId())
                    .ifPresent(s -> desc.append(" · ").append(s.getTotalScores()).append(" scores"));

            description = desc.toString();
        }
        return buildHtml("website", title, description, image, url);
    }

    private String buildDefaultHtml(String url) {
        return buildHtml("website", SITE_NAME, "Accuracy-based Beat Saber leaderboards", null, url);
    }

    private String buildHtml(String type, String title, String description, String image, String url) {
        String safeTitle = escapeHtml(title);
        String safeDesc = escapeHtml(description);
        StringBuilder sb = new StringBuilder("<!DOCTYPE html><html><head>");
        sb.append("<meta property=\"og:type\" content=\"").append(type).append("\"/>");
        sb.append("<meta property=\"og:title\" content=\"").append(safeTitle).append("\"/>");
        sb.append("<meta property=\"og:description\" content=\"").append(safeDesc).append("\"/>");
        if (image != null) {
            sb.append("<meta property=\"og:image\" content=\"").append(escapeHtml(image)).append("\"/>");
        }
        sb.append("<meta property=\"og:url\" content=\"").append(url).append("\"/>");
        sb.append("<meta name=\"twitter:card\" content=\"summary_large_image\"/>");
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
