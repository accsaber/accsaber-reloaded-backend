package com.accsaber.backend.model.entity.item;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.accsaber.backend.model.entity.user.User;

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
@Table(name = "user_crate_opens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCrateOpen {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "crate_item_id", nullable = false)
    private Item crateItem;

    @Column(name = "consumed_link_id", nullable = false)
    private UUID consumedLinkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_link_id")
    private UserItemLink rewardLink;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reward_item_id", nullable = false)
    private Item rewardItem;

    @Column(name = "roll_seed", nullable = false)
    private Long rollSeed;

    @CreationTimestamp
    @Column(name = "rolled_at", nullable = false, updatable = false)
    private Instant rolledAt;
}
