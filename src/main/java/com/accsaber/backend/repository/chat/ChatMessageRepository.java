package com.accsaber.backend.repository.chat;

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
