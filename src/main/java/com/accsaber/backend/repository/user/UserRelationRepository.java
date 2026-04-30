package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.user.UserRelation;
import com.accsaber.backend.model.entity.user.UserRelationType;

@Repository
public interface UserRelationRepository extends JpaRepository<UserRelation, UUID> {

    Optional<UserRelation> findByUser_IdAndTargetUser_IdAndType(Long userId, Long targetUserId, UserRelationType type);

    boolean existsByUser_IdAndTargetUser_IdAndTypeAndActiveTrue(Long userId, Long targetUserId, UserRelationType type);

    Page<UserRelation> findByUser_IdAndTypeAndActiveTrue(Long userId, UserRelationType type, Pageable pageable);

    Page<UserRelation> findByUser_IdAndActiveTrue(Long userId, Pageable pageable);

    Page<UserRelation> findByTargetUser_IdAndTypeAndActiveTrue(Long targetUserId, UserRelationType type,
            Pageable pageable);

    List<UserRelation> findByUser_IdAndTargetUser_IdAndActiveTrue(Long userId, Long targetUserId);

    long countByUser_IdAndTypeAndActiveTrue(Long userId, UserRelationType type);

    long countByTargetUser_IdAndTypeAndActiveTrue(Long targetUserId, UserRelationType type);
}
