package com.accsaber.backend.model.entity.item;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

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
@Table(name = "item_modifiers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemModifier {

    public static final String NORMAL = "normal";
    public static final String UNIQUE = "unique";
    public static final String VINTAGE = "vintage";
    public static final String GENUINE = "genuine";
    public static final String STRANGE = "strange";
    public static final String UNUSUAL = "unusual";
    public static final String HAUNTED = "haunted";
    public static final String JOLLY = "jolly";
    public static final String COLLECTORS = "collectors";
    public static final String HOLOGRAPHIC = "holographic";
    public static final String DECORATED = "decorated";
    public static final String ASCENDANT = "ascendant";
    public static final String BATTLE_WORN = "battle_worn";
    public static final String FOUNDERS = "founders";

    public static final Set<String> PER_INSTANCE_KEYS = Set.of(STRANGE, FOUNDERS, DECORATED, BATTLE_WORN);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "color_hex", nullable = false)
    private String colorHex;

    @Column(name = "effect_spec", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode effectSpec;

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
