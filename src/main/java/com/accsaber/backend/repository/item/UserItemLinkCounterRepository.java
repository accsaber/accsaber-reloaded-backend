package com.accsaber.backend.repository.item;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.UserItemLinkCounter;

@Repository
public interface UserItemLinkCounterRepository
        extends JpaRepository<UserItemLinkCounter, UserItemLinkCounter.CounterId> {

    List<UserItemLinkCounter> findByUserItemLink_IdIn(Collection<UUID> linkIds);

    @Modifying
    @Query(value = """
            INSERT INTO user_item_link_counters (user_item_link_id, stat_key, value, updated_at)
            VALUES (:linkId, :statKey, :delta, NOW())
            ON CONFLICT (user_item_link_id, stat_key)
            DO UPDATE SET value = user_item_link_counters.value + EXCLUDED.value,
                          updated_at = NOW()
            """, nativeQuery = true)
    int incrementBy(
            @Param("linkId") UUID linkId,
            @Param("statKey") String statKey,
            @Param("delta") long delta);
}
