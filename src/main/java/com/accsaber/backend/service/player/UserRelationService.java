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
import com.accsaber.backend.model.dto.response.player.UserRelationResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelation;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.repository.user.UserRelationRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRelationService {

    private final UserRelationRepository relationRepository;
    private final UserRepository userRepository;

    public List<Long> findActiveTargetUserIds(Long userId, UserRelationType type) {
        return relationRepository.findByUser_IdAndTypeAndActiveTrue(userId, type, Pageable.unpaged())
                .map(r -> r.getTargetUser().getId())
                .getContent();
    }

    public Page<UserRelationResponse> findByUser(Long userId, UserRelationType type, boolean includeBlocked,
            Pageable pageable) {
        if (type == UserRelationType.blocked && !includeBlocked) {
            throw new ForbiddenException("Cannot view another user's blocked list");
        }
        Page<UserRelation> page = type != null
                ? relationRepository.findByUser_IdAndTypeAndActiveTrue(userId, type, pageable)
                : relationRepository.findByUser_IdAndActiveTrue(userId, pageable);
        return page.map(this::toResponse);
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
        User target = r.getTargetUser();
        return UserRelationResponse.builder()
                .id(r.getId())
                .userId(r.getUser().getId())
                .targetUserId(target.getId())
                .targetName(target.getName())
                .targetAvatarUrl(target.getAvatarUrl())
                .targetCountry(target.getCountry())
                .type(r.getType())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
