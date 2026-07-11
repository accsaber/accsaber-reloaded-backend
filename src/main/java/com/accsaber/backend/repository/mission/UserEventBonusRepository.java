package com.accsaber.backend.repository.mission;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.mission.UserEventBonus;

public interface UserEventBonusRepository extends JpaRepository<UserEventBonus, UUID> {

    boolean existsByEvent_IdAndUser_Id(UUID eventId, Long userId);
}
