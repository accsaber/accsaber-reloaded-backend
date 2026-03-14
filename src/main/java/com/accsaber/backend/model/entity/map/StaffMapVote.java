package com.accsaber.backend.model.entity.map;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "staff_map_votes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffMapVote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "map_difficulty_id", nullable = false)
    private MapDifficulty mapDifficulty;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(nullable = false)
    private VoteType vote;

    @Column(nullable = false)
    private MapVoteAction type;

    @Column(name = "suggested_complexity", precision = 10, scale = 6)
    private BigDecimal suggestedComplexity;

    @Column(name = "criteria_vote")
    private VoteType criteriaVote;

    @Column(name = "criteria_vote_override", nullable = false)
    @Builder.Default
    private boolean criteriaVoteOverride = false;

    @Column
    private String reason;

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
