package com.accsaber.backend.service.campaign;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.campaign.CampaignLeaderboardEntry;
import com.accsaber.backend.model.dto.response.campaign.CampaignLeaderboardPlayer;
import com.accsaber.backend.model.dto.response.campaign.CampaignNodeScoreEntry;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.campaign.CampaignLeaderboardBoard;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.campaign.CampaignLeaderboardRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignLeaderboardService {

    private final CampaignRepository campaignRepository;
    private final CampaignDifficultyRepository campaignDifficultyRepository;
    private final CampaignLeaderboardRepository leaderboardRepository;

    @Cacheable(value = "leaderboards", key = "'campaign:' + #campaignId + ':' + #board + ':' + #search + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<CampaignLeaderboardEntry> getBoard(UUID campaignId, CampaignLeaderboardBoard board, String search,
            Pageable pageable) {
        requireNonDraftCampaign(campaignId);
        String searchArg = search != null && !search.isBlank() ? search.trim() : null;
        Pageable paging = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Object[]> rows = switch (board) {
            case COMPLETIONS -> leaderboardRepository.completions(campaignId, paging);
            case AVG_ACCURACY -> leaderboardRepository.averagesByAccuracy(campaignId, paging);
            case AVG_AP -> leaderboardRepository.averagesByAp(campaignId, paging);
            case PROGRESS -> leaderboardRepository.progress(campaignId, searchArg, paging);
        };

        int totalNodes = board == CampaignLeaderboardBoard.PROGRESS
                ? (int) campaignDifficultyRepository.countByCampaign_IdAndBarrierFalseAndActiveTrue(campaignId)
                : 0;
        int startRank = (int) paging.getOffset() + 1;
        List<CampaignLeaderboardEntry> content = new ArrayList<>(rows.getNumberOfElements());
        int i = 0;
        for (Object[] row : rows.getContent()) {
            content.add(mapEntry(board, row, startRank + i, totalNodes));
            i++;
        }
        return new PageImpl<>(content, pageable, rows.getTotalElements());
    }

    @Cacheable(value = "leaderboards", key = "'campaignNode:' + #campaignId + ':' + #nodeId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<CampaignNodeScoreEntry> getNodeBoard(UUID campaignId, UUID nodeId, Pageable pageable) {
        requireNonDraftCampaign(campaignId);
        CampaignDifficulty node = campaignDifficultyRepository.findByIdAndActiveTrue(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignDifficulty", nodeId));
        if (!node.getCampaign().getId().equals(campaignId)) {
            throw new ResourceNotFoundException("CampaignDifficulty", nodeId);
        }
        Pageable paging = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<Object[]> rows = leaderboardRepository.nodeScores(nodeId, paging);
        int startRank = (int) paging.getOffset() + 1;
        List<CampaignNodeScoreEntry> content = new ArrayList<>(rows.getNumberOfElements());
        int i = 0;
        for (Object[] row : rows.getContent()) {
            content.add(CampaignNodeScoreEntry.builder()
                    .rank(startRank + i)
                    .player(player(row))
                    .score(asInt(row[5]))
                    .accuracy(asBigDecimal(row[6]))
                    .ap(asBigDecimal(row[7]))
                    .build());
            i++;
        }
        return new PageImpl<>(content, pageable, rows.getTotalElements());
    }

    private Campaign requireNonDraftCampaign(UUID campaignId) {
        Campaign campaign = campaignRepository.findByIdAndActiveTrue(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
        if (campaign.getStatus() == CampaignStatus.DRAFT) {
            throw new ValidationException("Draft campaigns do not have leaderboards");
        }
        return campaign;
    }

    private CampaignLeaderboardEntry mapEntry(CampaignLeaderboardBoard board, Object[] row, int rank, int totalNodes) {
        CampaignLeaderboardEntry.CampaignLeaderboardEntryBuilder builder = CampaignLeaderboardEntry.builder()
                .player(player(row));
        return switch (board) {
            case COMPLETIONS -> builder.rank(rank).completedAt(asInstant(row[5])).build();
            case AVG_ACCURACY, AVG_AP -> builder.rank(rank)
                    .averageAccuracy(asBigDecimal(row[5]))
                    .averageAp(asBigDecimal(row[6]))
                    .nodesCounted(asInt(row[7]))
                    .build();
            case PROGRESS -> builder
                    .progressStatus(UserCampaignStatus.fromDbValue(asString(row[5])))
                    .completedAt(asInstant(row[6]))
                    .completedNodes(asInt(row[7]))
                    .totalNodes(totalNodes)
                    .build();
        };
    }

    private static CampaignLeaderboardPlayer player(Object[] row) {
        return CampaignLeaderboardPlayer.builder()
                .userId(String.valueOf(asLong(row[0])))
                .userName(asString(row[1]))
                .country(asString(row[2]))
                .avatarUrl(asString(row[3]))
                .cdnAvatarUrl(asString(row[4]))
                .build();
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer asInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    private static Instant asInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unsupported temporal type: " + value.getClass());
    }
}
