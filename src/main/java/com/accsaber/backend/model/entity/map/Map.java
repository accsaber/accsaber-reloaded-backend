package com.accsaber.backend.model.entity.map;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maps")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Map {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "song_name", nullable = false)
    private String songName;

    @Column(name = "song_author", nullable = false)
    private String songAuthor;

    @Column(name = "song_hash", nullable = false)
    private String songHash;

    @Column(name = "map_author", nullable = false)
    private String mapAuthor;

    @Column(name = "beatsaver_code")
    private String beatsaverCode;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "map", fetch = FetchType.LAZY)
    private List<MapDifficulty> difficulties;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
