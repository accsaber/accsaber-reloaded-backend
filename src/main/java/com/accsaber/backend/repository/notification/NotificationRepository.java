package com.accsaber.backend.repository.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.notification.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query(value = """
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
              AND (:unreadOnly = FALSE OR n.readAt IS NULL)
            ORDER BY n.createdAt DESC
            """, countQuery = """
            SELECT COUNT(n) FROM Notification n
            WHERE n.user.id = :userId
              AND (:unreadOnly = FALSE OR n.readAt IS NULL)
            """)
    Page<Notification> findFeed(@Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);

    long countByUser_IdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :now
            WHERE n.id = :id AND n.user.id = :userId AND n.readAt IS NULL
            """)
    int markRead(@Param("id") UUID id, @Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO notifications (user_id, type, title, link_to)
            SELECT u.id, 'server', :title, :linkTo
            FROM users u
            WHERE u.active = TRUE
              AND u.banned = FALSE
              AND u.player_inactive = FALSE
              AND NOT EXISTS (
                    SELECT 1 FROM user_settings s
                    WHERE s.user_id = u.id
                      AND s.key = 'notifications.server'
                      AND s.value = 'false'::jsonb)
            """, nativeQuery = true)
    int broadcast(@Param("title") String title, @Param("linkTo") String linkTo);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.createdAt < :cutoff")
    int deleteReadOlderThan(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
