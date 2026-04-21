package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.map.VoteListResponse;
import com.accsaber.backend.model.dto.response.map.VoteResponse;
import com.accsaber.backend.model.entity.AutoCriteriaStatus;
import com.accsaber.backend.model.entity.CriteriaStatus;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.StaffMapVote;
import com.accsaber.backend.model.entity.map.VoteType;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.StaffMapVoteRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapVotingService {

    private static final Logger log = LoggerFactory.getLogger(MapVotingService.class);

    private final StaffMapVoteRepository voteRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final StaffUserRepository staffUserRepository;

    @Value("${accsaber.voting.rank-threshold:3}")
    private int rankThreshold;

    @Value("${accsaber.voting.reweight-threshold:3}")
    private int reweightThreshold;

    @Value("${accsaber.voting.unrank-threshold:3}")
    private int unrankThreshold;

    private record StaffInfo(String username, String avatarUrl) {
    }

    public VoteListResponse getVotes(UUID mapDifficultyId, MapVoteAction type) {
        List<StaffMapVote> voteEntities = voteRepository.findByMapDifficultyIdAndActiveTrue(mapDifficultyId);
        java.util.Map<UUID, StaffInfo> staffInfo = loadStaffInfo(voteEntities);

        List<VoteResponse> votes = voteEntities.stream()
                .filter(v -> v.getType() == type)
                .map(v -> toResponse(v, staffInfo.get(v.getStaffId())))
                .toList();

        boolean rankReady = isThresholdMet(mapDifficultyId, MapVoteAction.RANK, rankThreshold);
        boolean reweightReady = isThresholdMet(mapDifficultyId, MapVoteAction.REWEIGHT, reweightThreshold);
        boolean unrankReady = isThresholdMet(mapDifficultyId, MapVoteAction.UNRANK, unrankThreshold);

        int criteriaUpvotes = (int) voteEntities.stream()
                .filter(v -> v.getCriteriaVote() == VoteType.UPVOTE).count();
        int criteriaDownvotes = (int) voteEntities.stream()
                .filter(v -> v.getCriteriaVote() == VoteType.DOWNVOTE).count();
        VoteType headCriteriaVote = voteEntities.stream()
                .filter(StaffMapVote::isCriteriaVoteOverride)
                .map(StaffMapVote::getCriteriaVote)
                .findFirst()
                .orElse(null);

        return VoteListResponse.builder()
                .votes(votes)
                .rankReady(rankReady)
                .reweightReady(reweightReady)
                .unrankReady(unrankReady)
                .criteriaUpvotes(criteriaUpvotes)
                .criteriaDownvotes(criteriaDownvotes)
                .headCriteriaVote(headCriteriaVote)
                .build();
    }

    public Page<VoteResponse> getActivityFeed(Pageable pageable) {
        Page<StaffMapVote> votes = voteRepository.findAllActiveWithMap(pageable);
        List<UUID> staffIds = votes.getContent().stream()
                .map(StaffMapVote::getStaffId)
                .distinct()
                .toList();
        java.util.Map<UUID, StaffInfo> staffInfo = staffIds.isEmpty()
                ? java.util.Map.of()
                : staffUserRepository.findAllByIdWithUser(staffIds).stream()
                        .collect(java.util.stream.Collectors.toMap(StaffUser::getId,
                                s -> new StaffInfo(s.getUsername(),
                                        s.getUser() != null ? s.getUser().getAvatarUrl() : null)));
        return votes.map(v -> toResponse(v, staffInfo.get(v.getStaffId())));
    }

    @Transactional
    public VoteResponse castVote(UUID mapDifficultyId, UUID staffId, VoteType vote, MapVoteAction type,
            BigDecimal suggestedComplexity, String reason,
            VoteType criteriaVote, Boolean criteriaVoteOverride, StaffRole role) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));

        validateVoteTypeForStatus(type, difficulty.getStatus());
        validateSuggestedComplexity(type, suggestedComplexity);

        if (Boolean.TRUE.equals(criteriaVoteOverride) && role != StaffRole.RANKING_HEAD && role != StaffRole.ADMIN) {
            throw new ValidationException("Only RANKING_HEAD or ADMIN can override criteria votes");
        }

        StaffMapVote staffVote = voteRepository
                .findByMapDifficultyIdAndStaffIdAndActiveTrue(mapDifficultyId, staffId)
                .map(existing -> updateVote(existing, vote, type, suggestedComplexity, reason))
                .orElseGet(() -> buildVote(difficulty, staffId, vote, type, suggestedComplexity, reason));

        if (criteriaVote != null) {
            staffVote.setCriteriaVote(criteriaVote);
        }
        if (criteriaVoteOverride != null) {
            staffVote.setCriteriaVoteOverride(criteriaVoteOverride);
        }

        StaffMapVote saved = voteRepository.save(staffVote);

        recomputeCriteriaStatus(difficulty);

        if (type == MapVoteAction.RANK) {
            if (difficulty.getStatus() == MapDifficultyStatus.QUEUE) {
                tryAutoQualify(difficulty);
            } else if (difficulty.getStatus() == MapDifficultyStatus.QUALIFIED) {
                tryAutoDequalify(difficulty);
            }
        }

        StaffInfo info = staffUserRepository.findAllByIdWithUser(List.of(staffId)).stream()
                .findFirst()
                .map(s -> new StaffInfo(s.getUsername(),
                        s.getUser() != null ? s.getUser().getAvatarUrl() : null))
                .orElse(null);
        return toResponse(saved, info);
    }

    @Transactional
    public void deactivateVote(UUID difficultyId, UUID voteId) {
        StaffMapVote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new ResourceNotFoundException("Vote", voteId));
        if (!vote.getMapDifficulty().getId().equals(difficultyId)) {
            throw new ResourceNotFoundException("Vote", voteId);
        }
        vote.setActive(false);
        voteRepository.save(vote);

        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(difficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", difficultyId));
        recomputeCriteriaStatus(difficulty);
    }

    public BigDecimal aggregateSuggestedComplexity(UUID mapDifficultyId) {
        List<StaffMapVote> reweightVotes = voteRepository
                .findByMapDifficultyIdAndActiveTrue(mapDifficultyId).stream()
                .filter(v -> v.getType() == MapVoteAction.REWEIGHT && v.getSuggestedComplexity() != null)
                .toList();

        if (reweightVotes.isEmpty())
            return null;

        BigDecimal sum = reweightVotes.stream()
                .map(StaffMapVote::getSuggestedComplexity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(reweightVotes.size()), 6, RoundingMode.HALF_UP);
    }

    private void tryAutoQualify(MapDifficulty difficulty) {
        UUID diffId = difficulty.getId();
        if (!isThresholdMet(diffId, MapVoteAction.RANK, rankThreshold)) {
            return;
        }
        if (!isCriteriaPassed(difficulty)) {
            return;
        }
        difficulty.setStatus(MapDifficultyStatus.QUALIFIED);
        mapDifficultyRepository.save(difficulty);
        log.info("Auto-qualified difficulty {}", diffId);
    }

    private void tryAutoDequalify(MapDifficulty difficulty) {
        UUID diffId = difficulty.getId();
        if (isThresholdMet(diffId, MapVoteAction.RANK, rankThreshold) && isCriteriaPassed(difficulty)) {
            return;
        }
        difficulty.setStatus(MapDifficultyStatus.QUEUE);
        mapDifficultyRepository.save(difficulty);
        log.info("Auto-dequalified difficulty {}", diffId);
    }

    private CriteriaStatus resolveCriteriaStatus(MapDifficulty difficulty) {
        List<StaffMapVote> votes = voteRepository.findByMapDifficultyIdAndActiveTrue(difficulty.getId());
        VoteType headOverride = votes.stream()
                .filter(StaffMapVote::isCriteriaVoteOverride)
                .map(StaffMapVote::getCriteriaVote)
                .findFirst()
                .orElse(null);
        if (headOverride != null) {
            return headOverride == VoteType.UPVOTE ? CriteriaStatus.PASSED : CriteriaStatus.FAILED;
        }
        long up = votes.stream().filter(v -> v.getCriteriaVote() == VoteType.UPVOTE).count();
        long down = votes.stream().filter(v -> v.getCriteriaVote() == VoteType.DOWNVOTE).count();
        if (up > down) {
            return CriteriaStatus.PASSED;
        }
        if (down > up) {
            return CriteriaStatus.FAILED;
        }
        return CriteriaStatus.PENDING;
    }

    private void recomputeCriteriaStatus(MapDifficulty difficulty) {
        CriteriaStatus newStatus = resolveCriteriaStatus(difficulty);
        if (difficulty.getCriteriaStatus() != newStatus) {
            difficulty.setCriteriaStatus(newStatus);
            mapDifficultyRepository.save(difficulty);
        }
    }

    private boolean isCriteriaPassed(MapDifficulty difficulty) {
        List<StaffMapVote> votes = voteRepository.findByMapDifficultyIdAndActiveTrue(difficulty.getId());
        VoteType headOverride = votes.stream()
                .filter(StaffMapVote::isCriteriaVoteOverride)
                .map(StaffMapVote::getCriteriaVote)
                .findFirst()
                .orElse(null);
        if (headOverride != null) {
            return headOverride == VoteType.UPVOTE;
        }
        long up = votes.stream().filter(v -> v.getCriteriaVote() == VoteType.UPVOTE).count();
        long down = votes.stream().filter(v -> v.getCriteriaVote() == VoteType.DOWNVOTE).count();
        if (up + down > 0) {
            return up - down > 0;
        }

        return difficulty.getAutoCriteriaStatus() == AutoCriteriaStatus.PASSED;
    }

    private boolean isThresholdMet(UUID mapDifficultyId, MapVoteAction type, int threshold) {
        long upvotes = voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                mapDifficultyId, type, VoteType.UPVOTE);
        long downvotes = voteRepository.countByMapDifficultyIdAndTypeAndVoteAndActiveTrue(
                mapDifficultyId, type, VoteType.DOWNVOTE);
        return upvotes - downvotes >= threshold;
    }

    private void validateVoteTypeForStatus(MapVoteAction type, MapDifficultyStatus status) {
        boolean rankVote = type == MapVoteAction.RANK;
        boolean ranked = status == MapDifficultyStatus.RANKED;

        if (rankVote && ranked) {
            throw new ValidationException("RANK votes can only be cast on difficulties in QUEUE or QUALIFIED status");
        }
        if (!rankVote && !ranked) {
            throw new ValidationException("REWEIGHT and UNRANK votes can only be cast on RANKED difficulties");
        }
    }

    private void validateSuggestedComplexity(MapVoteAction type, BigDecimal suggestedComplexity) {
        if (type == MapVoteAction.REWEIGHT && suggestedComplexity == null) {
            throw new ValidationException("suggestedComplexity is required for REWEIGHT votes");
        }
        if (type == MapVoteAction.UNRANK && suggestedComplexity != null) {
            throw new ValidationException("suggestedComplexity is not valid for UNRANK votes");
        }
    }

    private StaffMapVote updateVote(StaffMapVote existing, VoteType vote, MapVoteAction type,
            BigDecimal suggestedComplexity, String reason) {
        existing.setVote(vote);
        existing.setType(type);
        existing.setSuggestedComplexity(suggestedComplexity);
        existing.setReason(reason);
        return existing;
    }

    private StaffMapVote buildVote(MapDifficulty difficulty, UUID staffId, VoteType vote, MapVoteAction type,
            BigDecimal suggestedComplexity, String reason) {
        return StaffMapVote.builder()
                .mapDifficulty(difficulty)
                .staffId(staffId)
                .vote(vote)
                .type(type)
                .suggestedComplexity(suggestedComplexity)
                .reason(reason)
                .build();
    }

    private java.util.Map<UUID, StaffInfo> loadStaffInfo(List<StaffMapVote> votes) {
        List<UUID> staffIds = votes.stream()
                .map(StaffMapVote::getStaffId)
                .distinct()
                .toList();
        if (staffIds.isEmpty())
            return java.util.Map.of();

        return staffUserRepository.findAllByIdWithUser(staffIds).stream()
                .collect(java.util.stream.Collectors.toMap(StaffUser::getId,
                        s -> new StaffInfo(s.getUsername(),
                                s.getUser() != null ? s.getUser().getAvatarUrl() : null)));
    }

    private VoteResponse toResponse(StaffMapVote v, StaffInfo info) {
        var map = v.getMapDifficulty().getMap();
        return VoteResponse.builder()
                .id(v.getId())
                .mapDifficultyId(v.getMapDifficulty().getId())
                .songName(map.getSongName())
                .songAuthor(map.getSongAuthor())
                .mapAuthor(map.getMapAuthor())
                .coverUrl(map.getCoverUrl())
                .staffId(v.getStaffId())
                .staffUsername(info != null ? info.username() : null)
                .staffAvatarUrl(info != null ? info.avatarUrl() : null)
                .vote(v.getVote())
                .type(v.getType())
                .suggestedComplexity(v.getSuggestedComplexity())
                .criteriaVote(v.getCriteriaVote())
                .criteriaVoteOverride(v.isCriteriaVoteOverride())
                .reason(v.getReason())
                .active(v.isActive())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
