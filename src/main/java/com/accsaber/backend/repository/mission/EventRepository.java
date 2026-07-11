package com.accsaber.backend.repository.mission;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.mission.Event;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @EntityGraph(attributePaths = "bonusItems")
    List<Event> findByActiveTrueOrderByStartsAtDesc();

    @EntityGraph(attributePaths = "bonusItems")
    List<Event> findAllByOrderByStartsAtDesc();

    @EntityGraph(attributePaths = "bonusItems")
    Optional<Event> findByIdAndActiveTrue(UUID id);

    @EntityGraph(attributePaths = "bonusItems")
    Optional<Event> findWithBonusItemsById(UUID id);

    @Query("""
            SELECT e FROM Event e
            LEFT JOIN FETCH e.bonusItems
            WHERE e.active = true
              AND e.startsAt <= :now
              AND e.endsAt > :now
            ORDER BY e.startsAt DESC
            """)
    List<Event> findLive(@Param("now") Instant now);

    @Query("""
            SELECT e FROM Event e
            LEFT JOIN FETCH e.bonusItems
            WHERE e.active = true
              AND e.startsAt > :now
            ORDER BY e.startsAt ASC
            """)
    List<Event> findUpcoming(@Param("now") Instant now);

    @Query("""
            SELECT e FROM Event e
            LEFT JOIN FETCH e.bonusItems
            WHERE e.active = true
              AND e.endsAt <= :now
            ORDER BY e.endsAt DESC
            """)
    List<Event> findPast(@Param("now") Instant now);
}
