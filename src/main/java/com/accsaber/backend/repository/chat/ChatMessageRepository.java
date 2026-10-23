package com.accsaber.backend.repository.chat;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.chat.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @EntityGraph(attributePaths = { "user" })
    Page<ChatMessage> findByCampaign_IdOrderByCreatedAtDesc(UUID campaignId, Pageable pageable);

    @EntityGraph(attributePaths = { "user", "subjectUser", "subjectClan", "war" })
    Page<ChatMessage> findByClan_IdOrderByCreatedAtDesc(UUID clanId, Pageable pageable);

    @EntityGraph(attributePaths = { "user", "subjectUser", "subjectClan", "war" })
    Page<ChatMessage> findByClan_IdAndEventIsNullOrderByCreatedAtDesc(UUID clanId, Pageable pageable);

    @EntityGraph(attributePaths = { "user", "subjectUser", "subjectClan", "war" })
    Page<ChatMessage> findByClan_IdAndEventIsNotNullOrderByCreatedAtDesc(UUID clanId, Pageable pageable);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM chat_messages
                WHERE clan_id = :clanId
                  AND created_at >= date_trunc('milliseconds', CAST(:since AS timestamptz)) + INTERVAL '1 millisecond'
                  AND user_id IS DISTINCT FROM :userId
                  AND (CAST(:events AS boolean) IS NULL OR (event IS NOT NULL) = CAST(:events AS boolean))
                LIMIT :cap
            ) unread
            """, nativeQuery = true)
    int countClanUnread(@Param("clanId") UUID clanId, @Param("userId") Long userId,
            @Param("since") Instant since, @Param("events") Boolean events, @Param("cap") int cap);

    @Query(value = """
            SELECT MAX(created_at) FROM chat_messages
            WHERE clan_id = :clanId
              AND (CAST(:events AS boolean) IS NULL OR (event IS NOT NULL) = CAST(:events AS boolean))
            """, nativeQuery = true)
    Instant findLatestClanMessageAt(@Param("clanId") UUID clanId, @Param("events") Boolean events);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM chat_messages
            WHERE id IN (
                SELECT id FROM chat_messages
                WHERE campaign_id = :campaignId
                ORDER BY created_at DESC, id DESC
                OFFSET :keep
            )
            """, nativeQuery = true)
    int pruneCampaignToNewest(@Param("campaignId") UUID campaignId, @Param("keep") int keep);
}
