package com.accsaber.backend.model.entity.item;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_item_trade_items", uniqueConstraints = @UniqueConstraint(name = "uq_user_item_trade_items_trade_link", columnNames = {
        "trade_id", "user_item_link_id" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserItemTradeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false)
    private UserItemTrade trade;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_item_link_id", nullable = false)
    private UserItemLink userItemLink;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TradeItemSide side;

    @Column(nullable = false)
    @Builder.Default
    private Long quantity = 1L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
