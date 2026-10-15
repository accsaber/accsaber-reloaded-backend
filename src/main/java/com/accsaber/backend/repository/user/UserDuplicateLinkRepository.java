package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.user.UserDuplicateLink;

public interface UserDuplicateLinkRepository extends JpaRepository<UserDuplicateLink, UUID> {

    Optional<UserDuplicateLink> findBySecondaryUser_Id(Long secondaryUserId);

    Optional<UserDuplicateLink> findFirstByPrimaryUser_IdAndMergedTrue(Long primaryUserId);

    boolean existsBySecondaryUser_Id(Long secondaryUserId);

    List<UserDuplicateLink> findByMergedFalse();
}
