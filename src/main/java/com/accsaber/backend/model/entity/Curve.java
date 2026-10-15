package com.accsaber.backend.model.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "curves")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Curve {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CurveType type = CurveType.FORMULA;

    private String formula;

    @Column(name = "x_parameter_name")
    private String xParameterName;

    @Column(name = "x_parameter_value")
    private Double xParameterValue;

    @Column(name = "y_parameter_name")
    private String yParameterName;

    @Column(name = "y_parameter_value")
    private Double yParameterValue;

    @Column(name = "z_parameter_name")
    private String zParameterName;

    @Column(name = "z_parameter_value")
    private Double zParameterValue;

    private Double scale;

    private Double shift;

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
