package com.accsaber.backend.repository.mission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionTemplate;

public interface MissionTemplateRepository extends JpaRepository<MissionTemplate, UUID> {

    List<MissionTemplate> findByActiveTrue();

    @EntityGraph(attributePaths = "xpCurve")
    List<MissionTemplate> findByPoolAndActiveTrue(MissionPool pool);

    Optional<MissionTemplate> findByCode(String code);

    @Query("""
            SELECT t FROM MissionTemplate t
            LEFT JOIN FETCH t.awardsItem
            WHERE t.event.id = :eventId
              AND t.active = true
            ORDER BY t.unlocksAt ASC NULLS FIRST, t.createdAt ASC
            """)
    List<MissionTemplate> findActiveByEvent(@Param("eventId") UUID eventId);

    long countByEvent_IdAndActiveTrue(UUID eventId);

    @Query("""
            SELECT DISTINCT t.event.id FROM MissionTemplate t
            WHERE t.active = true
              AND t.event.active = true
              AND t.unlocksAt > :from
              AND t.unlocksAt <= :to
            """)
    List<UUID> findEventIdsWithUnlocksBetween(@Param("from") Instant from, @Param("to") Instant to);
}
