package com.accsaber.backend.model.entity.user;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    private String country;

    @Column(name = "country_override", nullable = false)
    @Builder.Default
    private boolean countryOverride = false;

    @Column(name = "total_xp", nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal totalXp = BigDecimal.ZERO;

    @Column(name = "xp_ranking")
    private Integer xpRanking;

    @Column(name = "xp_country_ranking")
    private Integer xpCountryRanking;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean banned = false;

    @Column(name = "ss_inactive", nullable = false)
    @Builder.Default
    private boolean ssInactive = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
