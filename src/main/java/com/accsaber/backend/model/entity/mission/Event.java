package com.accsaber.backend.model.entity.mission;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.item.Item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "background_url")
    private String backgroundUrl;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "bonus_xp", nullable = false)
    @Builder.Default
    private Integer bonusXp = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "event_bonus_items",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "item_id"))
    @Builder.Default
    private List<Item> bonusItems = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isLive(Instant now) {
        return active && !startsAt.isAfter(now) && endsAt.isAfter(now);
    }

    public int weekOf(Instant instant) {
        return (int) (Duration.between(startsAt, instant).toDays() / 7) + 1;
    }

    public Integer currentWeek(Instant now) {
        return isLive(now) ? weekOf(now) : null;
    }

    public int totalWeeks() {
        return (int) Math.ceil(Duration.between(startsAt, endsAt).toSeconds() / (double) (7 * 86400));
    }
}
