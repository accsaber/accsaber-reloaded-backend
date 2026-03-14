package com.accsaber.backend.service.map;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.map.VoteListResponse;
import com.accsaber.backend.model.dto.response.map.VoteResponse;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficultyStatus;
import com.accsaber.backend.model.entity.map.MapVoteAction;
import com.accsaber.backend.model.entity.map.StaffMapVote;
import com.accsaber.backend.model.entity.map.VoteType;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.map.StaffMapVoteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapVotingService {

    private final StaffMapVoteRepository voteRepository;
    private final MapDifficultyRepository mapDifficultyRepository;

    @Value("${accsaber.voting.reweight-threshold:3}")
    private int reweightThreshold;

    @Value("${accsaber.voting.unrank-threshold:3}")
    private int unrankThreshold;

    public VoteListResponse getVotes(UUID mapDifficultyId) {
        List<VoteResponse> votes = voteRepository.findByMapDifficultyIdAndActiveTrue(mapDifficultyId).stream()
                .map(this::toResponse)
                .toList();

        boolean reweightReady = isThresholdMet(mapDifficultyId, MapVoteAction.REWEIGHT, reweightThreshold);
        boolean unrankReady = isThresholdMet(mapDifficultyId, MapVoteAction.UNRANK, unrankThreshold);

        return VoteListResponse.builder()
                .votes(votes)
                .reweightReady(reweightReady)
                .unrankReady(unrankReady)
                .build();
    }

    @Transactional
    public VoteResponse castVote(UUID mapDifficultyId, UUID staffId, VoteType vote, MapVoteAction type,
            BigDecimal suggestedComplexity, String reason) {
        MapDifficulty difficulty = mapDifficultyRepository.findByIdAndActiveTrue(mapDifficultyId)
                .orElseThrow(() -> new ResourceNotFoundException("MapDifficulty", mapDifficultyId));

        validateVoteTypeForStatus(type, difficulty.getStatus());
        validateSuggestedComplexity(type, suggestedComplexity);

        StaffMapVote staffVote = voteRepository
                .findByMapDifficultyIdAndStaffIdAndActiveTrue(mapDifficultyId, staffId)
                .map(existing -> updateVote(existing, vote, type, suggestedComplexity, reason))
                .orElseGet(() -> buildVote(difficulty, staffId, vote, type, suggestedComplexity, reason));

        return toResponse(voteRepository.save(staffVote));
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
        if (type != MapVoteAction.REWEIGHT && suggestedComplexity != null) {
            throw new ValidationException("suggestedComplexity is only valid for REWEIGHT votes");
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

    private VoteResponse toResponse(StaffMapVote v) {
        return VoteResponse.builder()
                .id(v.getId())
                .mapDifficultyId(v.getMapDifficulty().getId())
                .staffId(v.getStaffId())
                .vote(v.getVote())
                .type(v.getType())
                .suggestedComplexity(v.getSuggestedComplexity())
                .criteriaVote(v.getCriteriaVote())
                .criteriaVoteOverride(v.isCriteriaVoteOverride())
                .reason(v.getReason())
                .active(v.isActive())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
