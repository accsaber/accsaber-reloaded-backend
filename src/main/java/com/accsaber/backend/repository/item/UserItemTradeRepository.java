package com.accsaber.backend.repository.item;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.TradeStatus;
import com.accsaber.backend.model.entity.item.UserItemTrade;

@Repository
public interface UserItemTradeRepository extends JpaRepository<UserItemTrade, UUID> {

       @Query("""
                     SELECT t FROM UserItemTrade t
                     WHERE t.status = com.accsaber.backend.model.entity.item.TradeStatus.pending
                       AND t.createdAt < :cutoff
                       AND t.offeredEssence > 0
                     """)
       List<UserItemTrade> findExpiringWithReservedEssence(@Param("cutoff") Instant cutoff);

       @Override
       @EntityGraph(attributePaths = { "fromUser", "toUser" }, type = EntityGraph.EntityGraphType.LOAD)
       Optional<UserItemTrade> findById(UUID id);

       @EntityGraph(attributePaths = { "fromUser", "toUser" }, type = EntityGraph.EntityGraphType.LOAD)
       @Query("SELECT t FROM UserItemTrade t WHERE " +
                     "((:incoming = TRUE AND t.toUser.id = :userId) OR (:outgoing = TRUE AND t.fromUser.id = :userId)) "
                     +
                     "AND t.status IN :statuses")
       Page<UserItemTrade> findByDirectionAndStatusIn(
                     @Param("userId") Long userId,
                     @Param("incoming") boolean incoming,
                     @Param("outgoing") boolean outgoing,
                     @Param("statuses") Collection<TradeStatus> statuses,
                     Pageable pageable);

       @Modifying
       @Query("UPDATE UserItemTrade t SET t.status = com.accsaber.backend.model.entity.item.TradeStatus.expired, " +
                     "t.resolvedAt = :now WHERE t.status = com.accsaber.backend.model.entity.item.TradeStatus.pending "
                     +
                     "AND t.createdAt < :cutoff")
       int expirePending(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
