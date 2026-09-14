package com.accsaber.backend.model.entity.mission;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.user.User;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_missions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "template_id", nullable = false)
    private MissionTemplate template;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MissionPool pool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clan_id")
    private Clan clan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_mission_id")
    private UserMission parentMission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_map_difficulty_id")
    private MapDifficulty targetMapDifficulty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_player_id")
    private User targetPlayer;

    @Column(name = "target_acc")
    private Double targetAcc;

    @Column(name = "target_ap")
    private Double targetAp;

    @Column(name = "target_score")
    private Integer targetScore;

    @Column(name = "target_count")
    private Integer targetCount;

    @Column(name = "target_xp")
    private Integer targetXp;

    @Column(name = "target_threshold_ap")
    private Double targetThresholdAp;

    @Column(name = "target_streak")
    private Integer targetStreak;

    @Column(name = "target_ranked_before")
    private Instant targetRankedBefore;

    @Column(name = "target_curated_only")
    private Boolean targetCuratedOnly;

    @Column(name = "snipe_distance")
    private Double snipeDistance;

    @Column(name = "assigned_skill_level")
    private Double assignedSkillLevel;

    @Column(name = "assigned_skill_threshold")
    private Double assignedSkillThreshold;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MissionBand band = MissionBand.medium;

    @Column(name = "progress_count", nullable = false)
    @Builder.Default
    private Integer progressCount = 0;

    @Column(name = "progress_ap", nullable = false)
    @Builder.Default
    private double progressAp = 0.0;

    @Column(name = "xp_reward", nullable = false)
    @Builder.Default
    private Integer xpReward = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_reward_id")
    private Item itemReward;

    @Column(name = "item_awarded", nullable = false)
    @Builder.Default
    private boolean itemAwarded = false;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MissionStatus status = MissionStatus.active;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public boolean isCommunity() {
        return pool == MissionPool.community;
    }

    public boolean isShared() {
        return isCommunity() || (pool == MissionPool.clan && parentMission == null);
    }
}
