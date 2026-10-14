package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.user.UserDuplicateLink;

@Repository
public interface UserDuplicateLinkRepository extends JpaRepository<UserDuplicateLink, UUID> {

    Optional<UserDuplicateLink> findBySecondaryUser_Id(Long secondaryUserId);

    Optional<UserDuplicateLink> findFirstByPrimaryUser_IdAndMergedTrue(Long primaryUserId);

    boolean existsBySecondaryUser_Id(Long secondaryUserId);

    List<UserDuplicateLink> findByMergedFalse();
}
