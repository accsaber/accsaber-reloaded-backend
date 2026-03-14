package com.accsaber.backend.model.entity.milestone;

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
@Table(name = "level_thresholds")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LevelThreshold {

    @Id
    private Integer level;

    @Column(name = "xp_required", nullable = false, precision = 20, scale = 6)
    private BigDecimal xpRequired;

    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
