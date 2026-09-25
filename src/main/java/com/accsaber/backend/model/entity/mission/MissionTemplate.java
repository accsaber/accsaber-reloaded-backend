package com.accsaber.backend.model.entity.mission;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.entity.Curve;

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
@Table(name = "mission_templates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MissionType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MissionPool pool;

    @Column(nullable = false)
    @Builder.Default
    private Integer weight = 100;

    @Column(name = "guaranteed_doable", nullable = false)
    @Builder.Default
    private boolean guaranteedDoable = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "xp_curve_id")
    private Curve xpCurve;

    @Column(name = "xp_multiplier", nullable = false)
    @Builder.Default
    private double xpMultiplier = 1.0;

    @Column(name = "band_easy", nullable = false)
    @Builder.Default
    private double bandEasy = 0.92;

    @Column(name = "band_medium", nullable = false)
    @Builder.Default
    private double bandMedium = 1.0;

    @Column(name = "band_hard", nullable = false)
    @Builder.Default
    private double bandHard = 1.08;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "awards_item_id")
    private com.accsaber.backend.model.entity.item.Item awardsItem;

    @Column(name = "target_count_min")
    private Integer targetCountMin;

    @Column(name = "target_count_max")
    private Integer targetCountMax;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "unlocks_at")
    private Instant unlocksAt;

    @Column(name = "completable_until")
    private Instant completableUntil;

    @Column(nullable = false)
    @Builder.Default
    private boolean repeatable = false;

    @Column(name = "max_completions")
    private Integer maxCompletions;

    @Column(name = "fixed_xp")
    private Integer fixedXp;

    @Column(name = "event_targets", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private EventMissionTargets eventTargets;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Instant unlockInstant(Event forEvent) {
        if (unlocksAt != null) {
            return unlocksAt;
        }
        return forEvent != null ? forEvent.getStartsAt() : createdAt;
    }

    public Instant closeInstant(Event forEvent) {
        if (forEvent == null) {
            return completableUntil;
        }
        Instant until = completableUntil != null ? completableUntil : forEvent.getEndsAt();
        return until.isBefore(forEvent.getEndsAt()) ? until : forEvent.getEndsAt();
    }

    public boolean isOpenAt(Event forEvent, Instant now) {
        Instant unlock = unlockInstant(forEvent);
        Instant close = closeInstant(forEvent);
        return unlock != null && !unlock.isAfter(now) && close != null && close.isAfter(now);
    }

    public int weekOf(Event forEvent) {
        return forEvent != null ? forEvent.weekOf(unlockInstant(forEvent)) : 1;
    }

    public boolean isCommunity() {
        return pool == MissionPool.community;
    }

    public boolean isEventTied() {
        return event != null;
    }
}
