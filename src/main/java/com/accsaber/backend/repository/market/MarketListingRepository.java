package com.accsaber.backend.repository.market;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.item.ItemRarity;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;

import jakarta.persistence.LockModeType;

@Repository
public interface MarketListingRepository extends JpaRepository<MarketListing, UUID> {

        @Query("""
                        SELECT l FROM MarketListing l
                        JOIN FETCH l.seller
                        LEFT JOIN FETCH l.currentBidder
                        LEFT JOIN FETCH l.userItemLink link
                        LEFT JOIN link.unusualEffect effect
                        JOIN FETCH l.item i
                        JOIN FETCH i.type t
                        LEFT JOIN t.parentType pt
                        WHERE l.status = :status
                        AND (:sellerId IS NULL OR l.seller.id = :sellerId)
                        AND (:typeKeys IS NULL OR t.key IN :typeKeys OR pt.key IN :typeKeys)
                        AND (:rarities IS NULL OR i.rarity IN :rarities)
                        AND (:modifierKeys IS NULL OR EXISTS (
                                SELECT m FROM link.modifiers m WHERE m.key IN :modifierKeys))
                        AND (:effectKeys IS NULL OR effect.key IN :effectKeys)
                        AND (:auctionsOnly = FALSE OR l.startingBid IS NOT NULL)
                        AND (:buyoutOnly = FALSE OR l.buyoutPrice IS NOT NULL)
                        AND (:minPrice IS NULL
                                OR COALESCE(l.currentBid, l.startingBid, l.buyoutPrice) >= :minPrice)
                        AND (:maxPrice IS NULL
                                OR COALESCE(l.currentBid, l.startingBid, l.buyoutPrice) <= :maxPrice)
                        AND (CAST(:search AS string) IS NULL
                                OR LOWER(l.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                        """)
        Page<MarketListing> browse(
                        @Param("status") MarketListingStatus status,
                        @Param("sellerId") Long sellerId,
                        @Param("typeKeys") Collection<String> typeKeys,
                        @Param("rarities") Collection<ItemRarity> rarities,
                        @Param("modifierKeys") Collection<String> modifierKeys,
                        @Param("effectKeys") Collection<String> effectKeys,
                        @Param("auctionsOnly") boolean auctionsOnly,
                        @Param("buyoutOnly") boolean buyoutOnly,
                        @Param("minPrice") Long minPrice,
                        @Param("maxPrice") Long maxPrice,
                        @Param("search") String search,
                        Pageable pageable);

        @Query("""
                        SELECT l FROM MarketListing l
                        JOIN FETCH l.seller
                        LEFT JOIN FETCH l.currentBidder
                        LEFT JOIN FETCH l.winner
                        LEFT JOIN FETCH l.userItemLink
                        JOIN FETCH l.item i
                        JOIN FETCH i.type
                        WHERE l.id = :id
                        """)
        Optional<MarketListing> findByIdHydrated(@Param("id") UUID id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT l FROM MarketListing l WHERE l.id = :id")
        Optional<MarketListing> findByIdForUpdate(@Param("id") UUID id);

        @Query(value = """
                        SELECT id FROM market_listings
                        WHERE status = 'active' AND ends_at IS NOT NULL AND ends_at <= :now
                        ORDER BY ends_at
                        FOR UPDATE SKIP LOCKED
                        LIMIT :batchSize
                        """, nativeQuery = true)
        List<UUID> claimDueForSettlement(@Param("now") Instant now, @Param("batchSize") int batchSize);

        long countBySeller_IdAndStatus(Long sellerId, MarketListingStatus status);

        @Query("""
                        SELECT l FROM MarketListing l
                        JOIN FETCH l.seller
                        LEFT JOIN FETCH l.currentBidder
                        LEFT JOIN FETCH l.winner
                        LEFT JOIN FETCH l.userItemLink
                        JOIN FETCH l.item i
                        JOIN FETCH i.type
                        WHERE l.status IN :statuses
                        AND (l.seller.id = :userId OR l.currentBidder.id = :userId OR l.winner.id = :userId)
                        """)
        Page<MarketListing> findInvolvingUser(
                        @Param("userId") Long userId,
                        @Param("statuses") Collection<MarketListingStatus> statuses,
                        Pageable pageable);
}
