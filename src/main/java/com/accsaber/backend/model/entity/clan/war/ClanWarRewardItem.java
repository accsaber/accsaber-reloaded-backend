package com.accsaber.backend.model.entity.clan.war;

import java.util.UUID;

import com.accsaber.backend.model.entity.item.Item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clan_war_reward_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClanWarRewardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    @Column(name = "top_contributors")
    private Integer topContributors;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
