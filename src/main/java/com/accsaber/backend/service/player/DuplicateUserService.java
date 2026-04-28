package com.accsaber.backend.service.player;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.player.DuplicateCandidateResponse;
import com.accsaber.backend.model.dto.response.player.DuplicateLinkResponse;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.score.ScoreModifierLink;
import com.accsaber.backend.model.entity.user.MergeScoreAction;
import com.accsaber.backend.model.entity.user.MergeScoreAction.ActionType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserDuplicateLink;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.score.ScoreModifierLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.MergeScoreActionRepository;
import com.accsaber.backend.repository.user.UserDuplicateLinkRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.skill.SkillService;
import com.accsaber.backend.service.stats.OverallStatisticsService;
import com.accsaber.backend.service.stats.RankingService;
import com.accsaber.backend.service.stats.StatisticsService;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DuplicateUserService {

    private static final Logger log = LoggerFactory.getLogger(DuplicateUserService.class);

    private final UserDuplicateLinkRepository linkRepository;
    private final MergeScoreActionRepository mergeScoreActionRepository;
    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final ScoreModifierLinkRepository modifierLinkRepository;
    private final StaffUserRepository staffUserRepository;
    private final CategoryRepository categoryRepository;
    private final StatisticsService statisticsService;
    private final OverallStatisticsService overallStatisticsService;
    private final RankingService rankingService;
    private final SkillService skillService;
    private final EntityManager entityManager;

    private DuplicateUserService self;

    @Autowired
    @Lazy
    public void setSelf(DuplicateUserService self) {
        this.self = self;
    }

    private final ConcurrentHashMap<Long, Long> duplicateCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        duplicateCache.clear();
        linkRepository.findAll().forEach(
                link -> duplicateCache.put(link.getSecondaryUser().getId(), link.getPrimaryUser().getId()));
        log.info("Loaded {} duplicate user links into cache", duplicateCache.size());
    }

    public Long resolvePrimaryUserId(Long userId) {
        return duplicateCache.getOrDefault(userId, userId);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<DuplicateCandidateResponse> detectDuplicates() {
        List<Object[]> rows = entityManager
                .createNativeQuery(
                        """
                                WITH identical_scores AS (
                                    SELECT s1.user_id AS u1_id, s2.user_id AS u2_id,
                                        COUNT(*) AS identical_count
                                    FROM scores s1
                                    JOIN scores s2 ON s1.map_difficulty_id = s2.map_difficulty_id
                                                AND s1.score = s2.score
                                                AND s1.user_id < s2.user_id
                                                AND s2.active = true
                                    WHERE s1.active = true
                                    GROUP BY s1.user_id, s2.user_id
                                    HAVING COUNT(*) >= 8
                                ),
                                user_score_counts AS (
                                    SELECT user_id, COUNT(*) AS total_scores
                                    FROM scores
                                    WHERE active = true
                                    GROUP BY user_id
                                ),
                                bl_counts AS (
                                    SELECT user_id, COUNT(*) AS bl_scores
                                    FROM scores
                                    WHERE bl_score_id IS NOT NULL AND active = true
                                    GROUP BY user_id
                                )
                                SELECT u1.id, u1.name, u2.id, u2.name, u1.country,
                                    iq.identical_count,
                                    COALESCE(uc1.total_scores, 0) AS u1_total_scores,
                                    COALESCE(uc2.total_scores, 0) AS u2_total_scores,
                                    COALESCE(bc1.bl_scores, 0) AS u1_bl_scores,
                                    COALESCE(bc2.bl_scores, 0) AS u2_bl_scores
                                FROM identical_scores iq
                                JOIN users u1 ON u1.id = iq.u1_id AND u1.active = true
                                JOIN users u2 ON u2.id = iq.u2_id AND u2.active = true
                                LEFT JOIN user_score_counts uc1 ON uc1.user_id = u1.id
                                LEFT JOIN user_score_counts uc2 ON uc2.user_id = u2.id
                                LEFT JOIN bl_counts bc1 ON bc1.user_id = u1.id
                                LEFT JOIN bl_counts bc2 ON bc2.user_id = u2.id
                                WHERE NOT EXISTS (
                                    SELECT 1 FROM users_duplicate_links udl
                                    WHERE udl.secondary_user_id = u1.id OR udl.secondary_user_id = u2.id
                                )
                                ORDER BY iq.identical_count DESC
                                """)
                .getResultList();

        return rows.stream().map(row -> {
            Long u1Id = ((Number) row[0]).longValue();
            String u1Name = (String) row[1];
            Long u2Id = ((Number) row[2]).longValue();
            String u2Name = (String) row[3];
            int u1TotalScores = ((Number) row[6]).intValue();
            int u2TotalScores = ((Number) row[7]).intValue();
            long u1BlScores = ((Number) row[8]).longValue();
            long u2BlScores = ((Number) row[9]).longValue();

            boolean u1IsPrimary = u1BlScores >= u2BlScores;
            return DuplicateCandidateResponse.builder()
                    .primaryUserId(String.valueOf(u1IsPrimary ? u1Id : u2Id))
                    .primaryUserName(u1IsPrimary ? u1Name : u2Name)
                    .secondaryUserId(String.valueOf(u1IsPrimary ? u2Id : u1Id))
                    .secondaryUserName(u1IsPrimary ? u2Name : u1Name)
                    .country((String) row[4])
                    .identicalScores(((Number) row[5]).intValue())
                    .primaryTotalScores(u1IsPrimary ? u1TotalScores : u2TotalScores)
                    .secondaryTotalScores(u1IsPrimary ? u2TotalScores : u1TotalScores)
                    .build();
        }).toList();
    }

    @Transactional
    public DuplicateLinkResponse createLink(Long primaryUserId, Long secondaryUserId, String reason) {
        validateLinkRequest(primaryUserId, secondaryUserId);

        User primary = userRepository.findById(primaryUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", primaryUserId));
        User secondary = userRepository.findById(secondaryUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", secondaryUserId));

        UserDuplicateLink link = linkRepository.save(UserDuplicateLink.builder()
                .primaryUser(primary)
                .secondaryUser(secondary)
                .reason(reason)
                .build());

        duplicateCache.put(secondaryUserId, primaryUserId);
        log.info("Created duplicate link: {} -> {}", secondaryUserId, primaryUserId);

        return toLinkResponse(link);
    }

    @Transactional
    public DuplicateLinkResponse merge(Long primaryUserId, Long secondaryUserId, UUID staffUserId, String reason) {
        User primary = userRepository.findById(primaryUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", primaryUserId));
        User secondary = userRepository.findById(secondaryUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", secondaryUserId));

        UserDuplicateLink link = linkRepository.findBySecondaryUser_Id(secondaryUserId)
                .orElseGet(() -> {
                    validateLinkRequest(primaryUserId, secondaryUserId);
                    return linkRepository.save(UserDuplicateLink.builder()
                            .primaryUser(primary)
                            .secondaryUser(secondary)
                            .build());
                });

        executeMerge(link, primary, secondary, staffUserId, reason);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                self.recalculateAfterMerge(primaryUserId);
            }
        });

        return toLinkResponse(link);
    }

    @Transactional
    public List<DuplicateLinkResponse> mergeAllUnmerged(UUID staffUserId) {
        List<UserDuplicateLink> unmerged = linkRepository.findByMergedFalse();
        if (unmerged.isEmpty()) {
            return List.of();
        }

        Set<Long> affectedPrimaryIds = new HashSet<>();
        List<DuplicateLinkResponse> results = unmerged.stream()
                .map(link -> {
                    executeMerge(link, link.getPrimaryUser(), link.getSecondaryUser(),
                            staffUserId, link.getReason());
                    affectedPrimaryIds.add(link.getPrimaryUser().getId());
                    return toLinkResponse(link);
                })
                .toList();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                self.recalculateAfterBulkMerge(affectedPrimaryIds);
            }
        });

        return results;
    }

    private void executeMerge(UserDuplicateLink link, User primary, User secondary,
            UUID staffUserId, String reason) {
        if (link.isMerged()) {
            throw new ValidationException("This duplicate link has already been merged");
        }

        int reassigned = reassignScores(secondary, primary, link);

        secondary.setActive(false);
        userRepository.save(secondary);

        link.setMerged(true);
        link.setMergedAt(Instant.now());
        link.setMergedBy(staffUserRepository.findById(staffUserId).orElse(null));
        link.setReason(reason);
        linkRepository.save(link);

        duplicateCache.put(secondary.getId(), primary.getId());
        log.info("Merged user {} into {}: {} scores reassigned", secondary.getId(), primary.getId(), reassigned);
    }

    @Transactional
    public void deleteUnmergedLink(UUID linkId) {
        UserDuplicateLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate link", linkId));
        if (link.isMerged()) {
            throw new ValidationException("Cannot delete a merged link");
        }
        duplicateCache.remove(link.getSecondaryUser().getId());
        linkRepository.delete(link);
    }

    @Transactional(readOnly = true)
    public List<DuplicateLinkResponse> listAllLinks() {
        return linkRepository.findAll().stream().map(this::toLinkResponse).toList();
    }

    @Transactional
    public DuplicateLinkResponse unmerge(UUID linkId) {
        UserDuplicateLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("Duplicate link", linkId));
        if (!link.isMerged()) {
            throw new ValidationException("This link has not been merged yet");
        }

        List<MergeScoreAction> actions = mergeScoreActionRepository.findByLink_Id(linkId);
        reverseActions(actions);
        mergeScoreActionRepository.deleteByLink_Id(linkId);

        User secondary = link.getSecondaryUser();
        secondary.setActive(true);
        userRepository.save(secondary);

        link.setMerged(false);
        link.setMergedAt(null);
        link.setMergedBy(null);
        linkRepository.save(link);

        duplicateCache.remove(secondary.getId());

        Long primaryUserId = link.getPrimaryUser().getId();
        Long secondaryUserId = secondary.getId();
        log.info("Unmerged user {} from {}: {} actions reversed", secondaryUserId, primaryUserId, actions.size());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                self.recalculateAfterUnmerge(primaryUserId, secondaryUserId);
            }
        });

        return toLinkResponse(link);
    }

    @Async("taskExecutor")
    public void recalculateAfterUnmerge(Long primaryUserId, Long secondaryUserId) {
        var categories = categoryRepository.findByActiveTrueAndCountForOverallTrue();
        for (var category : categories) {
            statisticsService.recalculate(primaryUserId, category.getId(), false, false);
            statisticsService.recalculate(secondaryUserId, category.getId(), false, false);
            rankingService.updateRankings(category.getId());
            skillService.upsertSkill(primaryUserId, category.getId());
            skillService.upsertSkill(secondaryUserId, category.getId());
        }
        overallStatisticsService.recalculate(primaryUserId, false);
        overallStatisticsService.recalculate(secondaryUserId, false);
        overallStatisticsService.updateOverallRankings();
    }

    private void reverseActions(List<MergeScoreAction> actions) {
        for (MergeScoreAction action : actions) {
            Score score = action.getScore();
            switch (action.getActionType()) {
                case CREATED_MERGED -> {
                    score.setActive(false);
                    scoreRepository.saveAndFlush(score);
                }
                case DEACTIVATED_SECONDARY, DEACTIVATED_PRIMARY -> {
                    score.setActive(true);
                    scoreRepository.saveAndFlush(score);
                }
            }
        }
    }

    private int reassignScores(User secondary, User primary, UserDuplicateLink link) {
        List<Score> secondaryScores = scoreRepository.findByUser_IdAndActiveTrue(secondary.getId());

        int count = 0;
        for (Score score : secondaryScores) {
            UUID mapDiffId = score.getMapDifficulty().getId();
            var primaryScore = scoreRepository
                    .findByUser_IdAndMapDifficulty_IdAndActiveTrue(primary.getId(), mapDiffId);

            score.setActive(false);
            scoreRepository.saveAndFlush(score);
            recordAction(link, ActionType.DEACTIVATED_SECONDARY, score);

            if (primaryScore.isEmpty()) {
                Score merged = createMergedScore(score, primary);
                recordAction(link, ActionType.CREATED_MERGED, merged);
                count++;
            } else if (score.getAp().compareTo(primaryScore.get().getAp()) > 0) {
                primaryScore.get().setActive(false);
                scoreRepository.saveAndFlush(primaryScore.get());
                recordAction(link, ActionType.DEACTIVATED_PRIMARY, primaryScore.get());
                Score merged = createMergedScore(score, primary);
                recordAction(link, ActionType.CREATED_MERGED, merged);
                count++;
            }
        }
        return count;
    }

    private void recordAction(UserDuplicateLink link, ActionType actionType, Score score) {
        mergeScoreActionRepository.save(MergeScoreAction.builder()
                .link(link)
                .actionType(actionType)
                .score(score)
                .build());
    }

    private Score createMergedScore(Score source, User primaryUser) {
        Score merged = Score.builder()
                .user(primaryUser)
                .mapDifficulty(source.getMapDifficulty())
                .score(source.getScore())
                .scoreNoMods(source.getScoreNoMods())
                .rank(source.getRank())
                .rankWhenSet(source.getRankWhenSet())
                .ap(source.getAp())
                .weightedAp(source.getWeightedAp())
                .blScoreId(source.getBlScoreId())
                .maxCombo(source.getMaxCombo())
                .badCuts(source.getBadCuts())
                .misses(source.getMisses())
                .wallHits(source.getWallHits())
                .bombHits(source.getBombHits())
                .pauses(source.getPauses())
                .streak115(source.getStreak115())
                .playCount(source.getPlayCount())
                .hmd(source.getHmd())
                .timeSet(source.getTimeSet())
                .xpGained(source.getXpGained())
                .supersedesReason("User merge")
                .active(true)
                .build();
        Score saved = scoreRepository.saveAndFlush(merged);
        copyModifierLinks(source, saved);
        return saved;
    }

    private void copyModifierLinks(Score from, Score to) {
        List<ScoreModifierLink> original = modifierLinkRepository.findByScore_Id(from.getId());
        if (original.isEmpty())
            return;
        List<ScoreModifierLink> copies = original.stream()
                .map(l -> ScoreModifierLink.builder().score(to).modifier(l.getModifier()).build())
                .toList();
        modifierLinkRepository.saveAll(copies);
    }

    @Async("taskExecutor")
    public void recalculateAfterMerge(Long primaryUserId) {
        recalculateAfterBulkMerge(Set.of(primaryUserId));
    }

    @Async("taskExecutor")
    public void recalculateAfterBulkMerge(Set<Long> primaryUserIds) {
        var categories = categoryRepository.findByActiveTrueAndCountForOverallTrue();
        for (var category : categories) {
            for (Long userId : primaryUserIds) {
                statisticsService.recalculate(userId, category.getId(), false, false);
            }
            rankingService.updateRankings(category.getId());
        }
        for (Long userId : primaryUserIds) {
            overallStatisticsService.recalculate(userId, false);
        }
        overallStatisticsService.updateOverallRankings();
        log.info("Bulk merge recalculation complete for {} users", primaryUserIds.size());
    }

    private void validateLinkRequest(Long primaryUserId, Long secondaryUserId) {
        if (primaryUserId.equals(secondaryUserId)) {
            throw new ValidationException("Cannot link a user to themselves");
        }
        if (linkRepository.existsBySecondaryUser_Id(secondaryUserId)) {
            throw new ValidationException("Secondary user is already linked to another primary");
        }
        if (linkRepository.existsBySecondaryUser_Id(primaryUserId)) {
            throw new ValidationException("Primary user is already a secondary in another link");
        }
    }

    private DuplicateLinkResponse toLinkResponse(UserDuplicateLink link) {
        return DuplicateLinkResponse.builder()
                .id(link.getId())
                .secondaryUserId(String.valueOf(link.getSecondaryUser().getId()))
                .secondaryUserName(link.getSecondaryUser().getName())
                .primaryUserId(String.valueOf(link.getPrimaryUser().getId()))
                .primaryUserName(link.getPrimaryUser().getName())
                .merged(link.isMerged())
                .mergedAt(link.getMergedAt())
                .reason(link.getReason())
                .createdAt(link.getCreatedAt())
                .build();
    }
}
