package com.accsaber.backend.repository.item;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.item.UserItemLinkCounter;

public interface UserItemLinkCounterRepository
        extends JpaRepository<UserItemLinkCounter, UserItemLinkCounter.CounterId> {

    List<UserItemLinkCounter> findByUserItemLink_IdIn(Collection<UUID> linkIds);

    default Map<UUID, Map<String, Long>> countersByLink(Collection<UUID> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<String, Long>> grouped = new HashMap<>();
        for (UserItemLinkCounter counter : findByUserItemLink_IdIn(linkIds)) {
            grouped.computeIfAbsent(counter.getId().getUserItemLinkId(), k -> new HashMap<>())
                    .put(counter.getId().getStatKey(), counter.getValue());
        }
        return grouped;
    }

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
