package com.accsaber.backend.repository.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.user.UserCategorySkillSnapshot;

public interface UserCategorySkillSnapshotRepository extends JpaRepository<UserCategorySkillSnapshot, UUID> {

    Optional<UserCategorySkillSnapshot> findFirstByUser_IdAndCategory_IdOrderByCapturedAtDesc(
            Long userId, UUID categoryId);
}
