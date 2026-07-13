package com.accsaber.backend.repository.mission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.mission.UserEventProfile;

public interface UserEventProfileRepository extends JpaRepository<UserEventProfile, UUID> {

    Optional<UserEventProfile> findByEvent_IdAndUser_Id(UUID eventId, Long userId);

    boolean existsByEvent_IdAndUser_Id(UUID eventId, Long userId);

    @Query("SELECT p.user.id FROM UserEventProfile p WHERE p.event.id = :eventId")
    List<Long> findUserIdsByEvent(@Param("eventId") UUID eventId);
}
