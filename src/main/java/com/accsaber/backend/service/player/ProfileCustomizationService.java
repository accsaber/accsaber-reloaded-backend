package com.accsaber.backend.service.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.user.PinnedMilestoneEntry;
import com.accsaber.backend.model.dto.request.user.PinnedScoreEntry;
import com.accsaber.backend.model.dto.request.user.ProfileUpdateRequest;
import com.accsaber.backend.model.entity.milestone.Milestone;
import com.accsaber.backend.model.entity.milestone.UserMilestoneLink;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserPinnedMilestone;
import com.accsaber.backend.model.entity.user.UserPinnedScore;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.milestone.MilestoneRepository;
import com.accsaber.backend.repository.milestone.UserMilestoneLinkRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserPinnedMilestoneRepository;
import com.accsaber.backend.repository.user.UserPinnedScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.media.CdnSyncService;
import com.accsaber.backend.service.supporter.SupporterService;

import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileCustomizationService {

    public static final int BASIC_MAX_PINNED_SCORES = 4;
    public static final int SUPPORTER_MAX_PINNED_SCORES = 8;
    public static final int BASIC_MAX_PINNED_MILESTONES = 4;
    public static final int SUPPORTER_MAX_PINNED_MILESTONES = 8;
    public static final int BASIC_MAX_BIO_LENGTH = 4000;
    public static final int SUPPORTER_MAX_BIO_LENGTH = 8000;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_PIN_COMMENT_LENGTH = 280;

    private final UserRepository userRepository;
    private final UserPinnedScoreRepository pinnedScoreRepository;
    private final UserPinnedMilestoneRepository pinnedMilestoneRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserMilestoneLinkRepository userMilestoneLinkRepository;
    private final ScoreRepository scoreRepository;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final RichTextSanitizer richTextSanitizer;
    private final SupporterService supporterService;
    private final CdnSyncService cdnSyncService;

    @Transactional
    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        if (request.getName() != null) {
            updateName(userId, request.getName());
        }
        if (request.getBio() != null) {
            updateBio(userId, request.getBio());
        }
        if (request.getPinnedScores() != null) {
            updatePinnedScores(userId, request.getPinnedScores());
        }
        if (request.getPinnedMilestones() != null) {
            updatePinnedMilestones(userId, request.getPinnedMilestones());
        }
    }

    @Transactional
    public void updateName(Long userId, String newName) {
        validateName(newName);
        User user = requireUser(userId);
        if (newName.equals(user.getName())) {
            return;
        }
        userService.updateProfile(userId, newName, null, null, null);
        userSettingsService.set(userId, UserSettingKey.SYNC_NAME, false);
    }

    @Transactional
    public String updateAvatar(Long userId, MultipartFile file) {
        requireUser(userId);
        return cdnSyncService.storeUserUploadedAvatar(userId, file);
    }

    @Transactional
    public void updateBio(Long userId, String rawHtml) {
        User user = requireUser(userId);
        boolean supporter = supporterService.isActiveSupporter(userId);
        int maxLength = supporter ? SUPPORTER_MAX_BIO_LENGTH : BASIC_MAX_BIO_LENGTH;
        user.setBio(richTextSanitizer.sanitize(rawHtml, maxLength, supporter));
        userRepository.save(user);
    }

    @Transactional
    public void updatePinnedScores(Long userId, List<PinnedScoreEntry> entries) {
        List<PinnedScoreEntry> normalized = entries == null ? List.of() : entries;
        int pinnedMax = pinnedMaxFor(userId);
        if (normalized.size() > pinnedMax) {
            throw new ValidationException("pinnedScores",
                    "may contain at most " + pinnedMax + " entries");
        }
        Set<UUID> uniqueScoreIds = new HashSet<>();
        Set<Integer> uniqueOrders = new HashSet<>();
        for (PinnedScoreEntry entry : normalized) {
            if (entry.scoreId() == null) {
                throw new ValidationException("pinnedScores", "scoreId must not be null");
            }
            if (!uniqueScoreIds.add(entry.scoreId())) {
                throw new ValidationException("pinnedScores", "duplicate scoreId " + entry.scoreId());
            }
            if (!uniqueOrders.add(entry.displayOrder())) {
                throw new ValidationException("pinnedScores", "duplicate displayOrder " + entry.displayOrder());
            }
        }
        User user = requireUser(userId);
        List<UserPinnedScore> built = normalized.stream()
                .map(entry -> buildPin(user, entry))
                .toList();
        pinnedScoreRepository.deleteByUser_Id(userId);
        pinnedScoreRepository.flush();
        pinnedScoreRepository.saveAll(built);
    }

    @Transactional
    public void updatePinnedMilestones(Long userId, List<PinnedMilestoneEntry> entries) {
        List<PinnedMilestoneEntry> normalized = entries == null ? List.of() : entries;
        int pinnedMax = pinnedMilestoneMaxFor(userId);
        if (normalized.size() > pinnedMax) {
            throw new ValidationException("pinnedMilestones",
                    "may contain at most " + pinnedMax + " entries");
        }
        Set<UUID> uniqueMilestoneIds = new HashSet<>();
        Set<Integer> uniqueOrders = new HashSet<>();
        for (PinnedMilestoneEntry entry : normalized) {
            if (entry.milestoneId() == null) {
                throw new ValidationException("pinnedMilestones", "milestoneId must not be null");
            }
            if (!uniqueMilestoneIds.add(entry.milestoneId())) {
                throw new ValidationException("pinnedMilestones", "duplicate milestoneId " + entry.milestoneId());
            }
            if (!uniqueOrders.add(entry.displayOrder())) {
                throw new ValidationException("pinnedMilestones", "duplicate displayOrder " + entry.displayOrder());
            }
        }
        User user = requireUser(userId);
        List<UserPinnedMilestone> built = normalized.stream()
                .map(entry -> buildMilestonePin(user, entry))
                .toList();
        pinnedMilestoneRepository.deleteByUser_Id(userId);
        pinnedMilestoneRepository.flush();
        pinnedMilestoneRepository.saveAll(built);
    }

    private UserPinnedMilestone buildMilestonePin(User user, PinnedMilestoneEntry entry) {
        Milestone milestone = milestoneRepository.findById(entry.milestoneId())
                .orElseThrow(() -> new ValidationException("pinnedMilestones",
                        "milestone not found: " + entry.milestoneId()));
        if (!milestone.isActive()) {
            throw new ValidationException("pinnedMilestones",
                    "milestone " + entry.milestoneId() + " is not active");
        }
        boolean completed = userMilestoneLinkRepository
                .findByUser_IdAndMilestone_Id(user.getId(), milestone.getId())
                .map(UserMilestoneLink::isCompleted)
                .orElse(false);
        if (!completed) {
            throw new ValidationException("pinnedMilestones",
                    "milestone " + entry.milestoneId() + " is not completed by the player");
        }
        return UserPinnedMilestone.builder()
                .user(user)
                .milestone(milestone)
                .displayOrder(entry.displayOrder())
                .build();
    }

    private int pinnedMilestoneMaxFor(Long userId) {
        return supporterService.isActiveSupporter(userId)
                ? SUPPORTER_MAX_PINNED_MILESTONES
                : BASIC_MAX_PINNED_MILESTONES;
    }

    private UserPinnedScore buildPin(User user, PinnedScoreEntry entry) {
        Score score = scoreRepository.findByIdWithUser(entry.scoreId())
                .orElseThrow(() -> new ValidationException("pinnedScores",
                        "score not found: " + entry.scoreId()));
        if (!score.getUser().getId().equals(user.getId())) {
            throw new ValidationException("pinnedScores",
                    "score " + entry.scoreId() + " does not belong to the player");
        }
        if (!score.isActive()) {
            throw new ValidationException("pinnedScores",
                    "score " + entry.scoreId() + " is not active");
        }
        return UserPinnedScore.builder()
                .user(user)
                .score(score)
                .displayOrder(entry.displayOrder())
                .comment(normalizeComment(entry.comment()))
                .build();
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_PIN_COMMENT_LENGTH) {
            throw new ValidationException("pinnedScores",
                    "comment must be at most " + MAX_PIN_COMMENT_LENGTH + " characters");
        }
        return trimmed;
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name",
                    "must be at most " + MAX_NAME_LENGTH + " characters");
        }
    }

    private int pinnedMaxFor(Long userId) {
        return supporterService.isActiveSupporter(userId)
                ? SUPPORTER_MAX_PINNED_SCORES
                : BASIC_MAX_PINNED_SCORES;
    }
}
