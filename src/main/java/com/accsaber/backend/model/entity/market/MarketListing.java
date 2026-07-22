package com.accsaber.backend.model.entity.market;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "market_listings", indexes = {
        @Index(name = "idx_market_listings_browse", columnList = "status, ends_at"),
        @Index(name = "idx_market_listings_seller", columnList = "seller_id, status"),
        @Index(name = "idx_market_listings_bidder", columnList = "current_bidder_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_item_link_id")
    private UserItemLink userItemLink;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "starting_bid")
    private Long startingBid;

    @Column(name = "buyout_price")
    private Long buyoutPrice;

    @Column(name = "min_increment", nullable = false)
    @Builder.Default
    private long minIncrement = 1L;

    @Column(name = "current_bid")
    private Long currentBid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_bidder_id")
    private User currentBidder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Column(name = "final_price")
    private Long finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MarketListingStatus status = MarketListingStatus.active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isAuction() {
        return startingBid != null;
    }

    public boolean isEndless() {
        return endsAt == null;
    }

    public long minimumAcceptableBid() {
        return currentBid == null ? startingBid : currentBid + minIncrement;
    }
}
