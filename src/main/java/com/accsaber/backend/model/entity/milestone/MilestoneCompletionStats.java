package com.accsaber.backend.model.entity.milestone;

import java.math.BigDecimal;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "milestone_completion_stats")
@Immutable
@Getter
@NoArgsConstructor
public class MilestoneCompletionStats {

    @Id
    @Column(name = "milestone_id")
    private UUID milestoneId;

    private Long completions;

    @Column(name = "total_players")
    private Long totalPlayers;

    @Column(name = "completion_percentage")
    private BigDecimal completionPercentage;
}
