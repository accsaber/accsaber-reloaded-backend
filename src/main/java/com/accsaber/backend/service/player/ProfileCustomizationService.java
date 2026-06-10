package com.accsaber.backend.service.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.user.PinnedScoreEntry;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserPinnedScore;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.score.ScoreRepository;
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

    public static final int BASIC_MAX_PINNED_SCORES = 3;
    public static final int SUPPORTER_MAX_PINNED_SCORES = 6;
    public static final int BASIC_MAX_BIO_LENGTH = 4000;
    public static final int SUPPORTER_MAX_BIO_LENGTH = 8000;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_PIN_COMMENT_LENGTH = 280;

    private final UserRepository userRepository;
    private final UserPinnedScoreRepository pinnedScoreRepository;
    private final ScoreRepository scoreRepository;
    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final BioSanitizer bioSanitizer;
    private final SupporterService supporterService;
    private final CdnSyncService cdnSyncService;

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
        user.setBio(bioSanitizer.sanitize(rawHtml, bioMaxFor(userId)));
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

    private int bioMaxFor(Long userId) {
        return supporterService.isActiveSupporter(userId)
                ? SUPPORTER_MAX_BIO_LENGTH
                : BASIC_MAX_BIO_LENGTH;
    }
}
