package com.accsaber.backend.service.player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.response.player.UserRelationCounts;
import com.accsaber.backend.model.dto.response.player.UserRelationResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelation;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.model.entity.user.Visibility;
import com.accsaber.backend.model.event.PlayersMergedEvent;
import com.accsaber.backend.repository.user.UserRelationRepository;
import com.accsaber.backend.repository.user.UserRelationRepository.RelationCountView;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.clan.ClanRefCache;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRelationService {

    private static final Set<UserSettingKey> VISIBILITY_KEYS = Set.of(
            UserSettingKey.PRIVACY_FOLLOWING_VISIBILITY, UserSettingKey.PRIVACY_RIVALS_VISIBILITY);

    private final UserRelationRepository relationRepository;
    private final UserRepository userRepository;
    private final UserSettingsService userSettingsService;

    private List<Long> findActiveTargetUserIds(Long userId, UserRelationType type) {
        return relationRepository.findByUser_IdAndTypeAndActiveTrue(userId, type, Pageable.unpaged())
                .map(r -> r.getTargetUser().getId())
                .getContent();
    }

    public List<Long> findActiveTargetUserIdsByTypes(Long userId, java.util.Collection<UserRelationType> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        return relationRepository.findActiveTargetUserIdsByTypes(userId, types);
    }

    private List<Long> findRelationFilterUserIds(Long userId, UserRelationType type) {
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

    public List<Long> resolveRelationFilter(UserRelationType relation, PlayerUserDetails principal) {
        if (relation == null) {
            return null;
        }
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required to filter by relation");
        }
        return findRelationFilterUserIds(principal.getUserId(), relation);
    }

    public UserRelationCounts countsFor(Long userId, Long viewerUserId) {
        boolean isSelf = userId.equals(viewerUserId);
        Map<UserSettingKey, Visibility> visibility = isSelf ? Map.of()
                : userSettingsService.get(userId, VISIBILITY_KEYS, Visibility.class);
        boolean follows = visibility.containsValue(Visibility.FOLLOWERS_ONLY) && isFollowerOf(viewerUserId, userId);
        Map<UserRelationType, RelationCountView> counts = relationRepository.countActiveByType(userId).stream()
                .collect(Collectors.toMap(RelationCountView::getType, row -> row));
        Predicate<UserRelationType> listVisible = type -> isSelf
                || canView(visibility.get(visibilityKey(type)), () -> follows);
        return UserRelationCounts.builder()
                .followingCount(listVisible.test(UserRelationType.follower)
                        ? count(counts, UserRelationType.follower, RelationCountView::getOutgoing) : null)
                .followerCount(count(counts, UserRelationType.follower, RelationCountView::getIncoming))
                .rivalCount(listVisible.test(UserRelationType.rival)
                        ? count(counts, UserRelationType.rival, RelationCountView::getOutgoing) : null)
                .rivaledByCount(count(counts, UserRelationType.rival, RelationCountView::getIncoming))
                .blockedCount(isSelf ? count(counts, UserRelationType.blocked, RelationCountView::getOutgoing) : null)
                .build();
    }

    private static long count(Map<UserRelationType, RelationCountView> counts, UserRelationType type,
            ToLongFunction<RelationCountView> side) {
        RelationCountView row = counts.get(type);
        return row == null ? 0 : side.applyAsLong(row);
    }

    private boolean isFollowerOf(Long viewerId, Long targetUserId) {
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
            Visibility visibility = userSettingsService.get(userId, visibilityKey(type), Visibility.class);
            if (!canView(visibility, () -> isFollowerOf(viewerUserId, userId))) {
                throw new ForbiddenException("This list is not visible");
            }
        }
        Page<UserRelation> page = type != null
                ? relationRepository.findByUser_IdAndTypeAndActiveTrue(userId, type, pageable)
                : relationRepository.findByUser_IdAndActiveTrue(userId, pageable);
        return page.map(this::toResponse);
    }

    private boolean canView(Visibility visibility, BooleanSupplier viewerFollowsOwner) {
        return switch (visibility) {
            case PUBLIC -> true;
            case PRIVATE -> false;
            case FOLLOWERS_ONLY -> viewerFollowsOwner.getAsBoolean();
        };
    }

    private UserSettingKey visibilityKey(UserRelationType type) {
        return type == UserRelationType.rival
                ? UserSettingKey.PRIVACY_RIVALS_VISIBILITY
                : UserSettingKey.PRIVACY_FOLLOWING_VISIBILITY;
    }

    public Page<UserRelationResponse> findByTarget(Long targetUserId, UserRelationType type, Long viewerUserId,
            Pageable pageable) {
        if (type == UserRelationType.blocked) {
            throw new ForbiddenException("Cannot list users who have blocked someone");
        }
        if (type == null) {
            throw new ValidationException("type is required for incoming relations");
        }
        Page<UserRelation> page = relationRepository
                .findByTargetUser_IdAndTypeAndActiveTrue(targetUserId, type, pageable);
        Set<Long> hidden = findHidingOwnerIds(page.getContent(), type, viewerUserId);
        return page.map(r -> hidden.contains(r.getUser().getId())
                ? toHiddenResponse(r)
                : toIncomingResponse(r));
    }

    private Set<Long> findHidingOwnerIds(List<UserRelation> relations, UserRelationType type, Long viewerUserId) {
        Set<Long> ownerIds = relations.stream()
                .map(r -> r.getUser().getId())
                .filter(id -> !id.equals(viewerUserId))
                .collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return Set.of();
        }
        Map<Long, Visibility> visibilities = userSettingsService.getMany(ownerIds, visibilityKey(type),
                Visibility.class);
        Set<Long> hiding = ownerIds.stream()
                .filter(id -> visibilities.get(id) != null && visibilities.get(id) != Visibility.PUBLIC)
                .collect(Collectors.toCollection(HashSet::new));
        if (hiding.isEmpty()) {
            return Set.of();
        }
        Set<Long> followedByViewer = viewerUserId != null
                && hiding.stream().anyMatch(id -> visibilities.get(id) == Visibility.FOLLOWERS_ONLY)
                        ? Set.copyOf(relationRepository.findActiveTargetUserIdsIn(viewerUserId,
                                UserRelationType.follower, hiding))
                        : Set.<Long>of();
        hiding.removeIf(id -> canView(visibilities.get(id), () -> followedByViewer.contains(id)));
        return hiding;
    }

    @Transactional
    public UserRelationResponse create(Long userId, Long targetUserId, UserRelationType type) {
        if (userId.equals(targetUserId)) {
            throw new ValidationException("Cannot create a relation to yourself");
        }
        User target = userRepository.requireActive(targetUserId);

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
            User user = userRepository.requireActive(userId);
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

    @EventListener
    @Transactional
    public void onPlayersMerged(PlayersMergedEvent event) {
        relationRepository.moveActiveRelations(event.secondaryUserId(), event.primaryUserId());
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

    private UserRelationResponse toHiddenResponse(UserRelation r) {
        return UserRelationResponse.builder()
                .id(r.getId())
                .userId(r.getTargetUser().getId())
                .targetName(r.getType() == UserRelationType.rival ? "Hidden Rival" : "Hidden Follower")
                .type(r.getType())
                .createdAt(r.getCreatedAt())
                .hidden(true)
                .build();
    }

    private UserRelationResponse buildResponse(UserRelation r, User from, User other) {
        return UserRelationResponse.builder()
                .id(r.getId())
                .userId(from.getId())
                .targetUserId(other.getId())
                .targetName(other.getName())
                .targetAvatarUrl(other.getAvatarUrl())
                .targetCdnAvatarUrl(other.getCdnAvatarUrl())
                .targetClan(ClanRefCache.forUser(other.getId()))
                .targetCountry(other.getCountry())
                .type(r.getType())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
