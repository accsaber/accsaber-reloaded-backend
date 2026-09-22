package com.accsaber.backend.model.entity.clan;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String tag;

    @Column(nullable = false)
    private String slug;

    private String description;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "tag_color")
    private String tagColor;

    @Column(name = "accepting_requests", nullable = false)
    @Builder.Default
    private boolean acceptingRequests = true;

    @Column(name = "total_xp", nullable = false)
    private double totalXp;

    @Column(name = "roster_strength", nullable = false)
    private double rosterStrength;

    @Column(name = "ally_strength", nullable = false)
    private double allyStrength;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
