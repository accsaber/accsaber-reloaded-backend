package com.accsaber.backend.repository.item;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemTradeItem;

public interface UserItemTradeItemRepository extends JpaRepository<UserItemTradeItem, UUID> {

    @Query("""
            SELECT ti.userItemLink.id FROM UserItemTradeItem ti
            WHERE ti.userItemLink.id IN :linkIds
              AND ti.trade.status = :status
            """)
    List<UUID> findLinkIdsInTradesWithStatus(
            @Param("linkIds") Collection<UUID> linkIds,
            @Param("status") TradeStatus status);

    @Query("SELECT ti.userItemLink.id FROM UserItemTradeItem ti WHERE ti.trade.id = :tradeId")
    List<UUID> findLinkIdsByTradeId(@Param("tradeId") UUID tradeId);
}
