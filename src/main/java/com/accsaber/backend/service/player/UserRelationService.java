package com.accsaber.backend.service.player;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.player.UserRelationCounts;
import com.accsaber.backend.model.dto.response.player.UserRelationResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelation;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.model.entity.user.Visibility;
import com.accsaber.backend.repository.user.UserRelationRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRelationService {

    private final UserRelationRepository relationRepository;
    private final UserRepository userRepository;
    private final UserSettingsService userSettingsService;

    public List<Long> findActiveTargetUserIds(Long userId, UserRelationType type) {
        return relationRepository.findByUser_IdAndTypeAndActiveTrue(userId, type, Pageable.unpaged())
                .map(r -> r.getTargetUser().getId())
                .getContent();
    }

    public List<Long> findRelationFilterUserIds(Long userId, UserRelationType type) {
        List<Long> targets = findActiveTargetUserIds(userId, type);
        if (type == UserRelationType.blocked) {
            return targets;
        }
        if (targets.contains(userId)) {
            return targets;
        }
        List<Long> withSelf = new java.util.ArrayList<>(targets.size() + 1);
        withSelf.add(userId);
        withSelf.addAll(targets);
        return withSelf;
    }

    public UserRelationCounts countsFor(Long userId, boolean isSelf) {
        return countsFor(userId, isSelf, true, true);
    }

    public UserRelationCounts countsFor(Long userId, boolean isSelf, boolean canSeeFollowing, boolean canSeeRivals) {
        long followerCount = relationRepository
                .countByTargetUser_IdAndTypeAndActiveTrue(userId, UserRelationType.follower);
        long rivaledByCount = relationRepository
                .countByTargetUser_IdAndTypeAndActiveTrue(userId, UserRelationType.rival);
        Long followingCount = (canSeeFollowing || isSelf)
                ? relationRepository.countByUser_IdAndTypeAndActiveTrue(userId, UserRelationType.follower)
                : null;
        Long rivalCount = (canSeeRivals || isSelf)
                ? relationRepository.countByUser_IdAndTypeAndActiveTrue(userId, UserRelationType.rival)
                : null;
        return UserRelationCounts.builder()
                .followingCount(followingCount)
                .followerCount(followerCount)
                .rivalCount(rivalCount)
                .rivaledByCount(rivaledByCount)
                .blockedCount(isSelf
                        ? relationRepository.countByUser_IdAndTypeAndActiveTrue(userId, UserRelationType.blocked)
                        : null)
                .build();
    }

    public boolean isFollowerOf(Long viewerId, Long targetUserId) {
        if (viewerId == null) return false;
        return relationRepository.existsByUser_IdAndTargetUser_IdAndTypeAndActiveTrue(
                viewerId, targetUserId, UserRelationType.follower);
    }

    public Page<UserRelationResponse> findByUser(Long userId, UserRelationType type, boolean includeBlocked,
            Pageable pageable) {
        return findByUser(userId, type, includeBlocked, null, pageable);
    }

    public Page<UserRelationResponse> findByUser(Long userId, UserRelationType type, boolean includeBlocked,
            Long viewerUserId, Pageable pageable) {
        if (type == UserRelationType.blocked && !includeBlocked) {
            throw new ForbiddenException("Cannot view another user's blocked list");
        }
        boolean isSelf = viewerUserId != null && viewerUserId.equals(userId);
        if (!isSelf && type != null && type != UserRelationType.blocked) {
            UserSettingKey settingKey = type == UserRelationType.follower
                    ? UserSettingKey.PRIVACY_FOLLOWING_VISIBILITY
                    : UserSettingKey.PRIVACY_RIVALS_VISIBILITY;
            Visibility visibility = userSettingsService.get(userId, settingKey, Visibility.class);
            if (!canView(viewerUserId, userId, visibility)) {
                throw new ForbiddenException("This list is not visible");
            }
        }
        Page<UserRelation> page = type != null
                ? relationRepository.findByUser_IdAndTypeAndActiveTrue(userId, type, pageable)
                : relationRepository.findByUser_IdAndActiveTrue(userId, pageable);
        return page.map(this::toResponse);
    }

    private boolean canView(Long viewerId, Long ownerId, Visibility visibility) {
        return switch (visibility) {
            case PUBLIC -> true;
            case PRIVATE -> false;
            case FOLLOWERS_ONLY -> viewerId != null && isFollowerOf(viewerId, ownerId);
        };
    }

    public Page<UserRelationResponse> findByTarget(Long targetUserId, UserRelationType type, Pageable pageable) {
        if (type == UserRelationType.blocked) {
            throw new ForbiddenException("Cannot list users who have blocked someone");
        }
        if (type == null) {
            throw new ValidationException("type is required for incoming relations");
        }
        return relationRepository.findByTargetUser_IdAndTypeAndActiveTrue(targetUserId, type, pageable)
                .map(this::toIncomingResponse);
    }

    @Transactional
    public UserRelationResponse create(Long userId, Long targetUserId, UserRelationType type) {
        if (userId.equals(targetUserId)) {
            throw new ValidationException("Cannot create a relation to yourself");
        }
        User target = userRepository.findByIdAndActiveTrue(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetUserId));

        if (type != UserRelationType.blocked
                && relationRepository.existsByUser_IdAndTargetUser_IdAndTypeAndActiveTrue(targetUserId, userId,
                        UserRelationType.blocked)) {
            throw new ForbiddenException("Target user has blocked you");
        }

        UserRelation relation = relationRepository
                .findByUser_IdAndTargetUser_IdAndType(userId, targetUserId, type)
                .orElse(null);
        if (relation != null && relation.isActive()) {
            throw new ConflictException("User relation", type + ":" + targetUserId);
        }
        if (relation == null) {
            User user = userRepository.findByIdAndActiveTrue(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", userId));
            relation = UserRelation.builder()
                    .user(user)
                    .targetUser(target)
                    .type(type)
                    .active(true)
                    .build();
        } else {
            relation.setActive(true);
        }
        UserRelation saved = relationRepository.save(relation);

        if (type == UserRelationType.blocked) {
            applyBlockSideEffects(userId, targetUserId);
        }

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long userId, UUID relationId) {
        UserRelation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new ResourceNotFoundException("UserRelation", relationId));
        if (!relation.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Cannot delete another user's relation");
        }
        if (!relation.isActive()) {
            return;
        }
        relation.setActive(false);
        relationRepository.save(relation);
    }

    private void applyBlockSideEffects(Long userId, Long targetUserId) {
        deactivateNonBlockRelations(userId, targetUserId);
        deactivateNonBlockRelations(targetUserId, userId);
    }

    private void deactivateNonBlockRelations(Long fromUserId, Long toUserId) {
        List<UserRelation> existing = relationRepository
                .findByUser_IdAndTargetUser_IdAndActiveTrue(fromUserId, toUserId);
        for (UserRelation r : existing) {
            if (r.getType() != UserRelationType.blocked) {
                r.setActive(false);
                relationRepository.save(r);
            }
        }
    }

    private UserRelationResponse toResponse(UserRelation r) {
        return buildResponse(r, r.getUser(), r.getTargetUser());
    }

    private UserRelationResponse toIncomingResponse(UserRelation r) {
        return buildResponse(r, r.getTargetUser(), r.getUser());
    }

    private UserRelationResponse buildResponse(UserRelation r, User from, User other) {
        return UserRelationResponse.builder()
                .id(r.getId())
                .userId(from.getId())
                .targetUserId(other.getId())
                .targetName(other.getName())
                .targetAvatarUrl(other.getAvatarUrl())
                .targetCountry(other.getCountry())
                .type(r.getType())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
