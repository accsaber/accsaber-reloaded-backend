package com.accsaber.backend.repository.market;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.market.MarketBid;

@Repository
public interface MarketBidRepository extends JpaRepository<MarketBid, UUID> {

    @Query("""
            SELECT b FROM MarketBid b
            JOIN FETCH b.bidder
            WHERE b.listing.id = :listingId
            ORDER BY b.amount DESC
            """)
    List<MarketBid> findByListingHydrated(@Param("listingId") UUID listingId);

    @Query(value = """
            SELECT b FROM MarketBid b
            JOIN FETCH b.bidder
            JOIN FETCH b.listing
            WHERE b.bidder.id = :bidderId
            ORDER BY b.createdAt DESC
            """, countQuery = "SELECT COUNT(b) FROM MarketBid b WHERE b.bidder.id = :bidderId")
    Page<MarketBid> findByBidderHydrated(@Param("bidderId") Long bidderId, Pageable pageable);

    long countByListing_Id(UUID listingId);

    @Query("SELECT b.listing.id, COUNT(b) FROM MarketBid b WHERE b.listing.id IN :listingIds GROUP BY b.listing.id")
    List<Object[]> countByListingIds(@Param("listingIds") Collection<UUID> listingIds);
}
