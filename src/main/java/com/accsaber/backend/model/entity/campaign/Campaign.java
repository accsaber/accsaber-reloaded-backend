package com.accsaber.backend.model.entity.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(name = "creator_alias")
    private String creatorAlias;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String summary;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "seeking_curation", nullable = false)
    @Builder.Default
    private boolean seekingCuration = false;

    @Column(name = "progression_agnostic", nullable = false)
    @Builder.Default
    private boolean progressionAgnostic = false;

    @Column(name = "completion_mode", nullable = false)
    @Builder.Default
    private CampaignCompletionMode completionMode = CampaignCompletionMode.TERMINAL;

    @Column(nullable = false)
    @Builder.Default
    private boolean legacy = false;

    @Column(name = "completion_xp", nullable = false, precision = 20, scale = 6)
    @Builder.Default
    private BigDecimal completionXp = BigDecimal.ZERO;

    @Column(name = "curator_notes")
    private String curatorNotes;

    @Column(name = "playlist_export_enabled", nullable = false)
    @Builder.Default
    private boolean playlistExportEnabled = false;

    @Column(name = "total_upvotes", nullable = false)
    @Builder.Default
    private int totalUpvotes = 0;

    @Column(name = "total_downvotes", nullable = false)
    @Builder.Default
    private int totalDownvotes = 0;

    @Column(name = "vote_score", nullable = false)
    @Builder.Default
    private double voteScore = 0.0;

    @Column(name = "background_url")
    private String backgroundUrl;

    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "icon_url")
    private String iconUrl;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "curated_at")
    private Instant curatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curated_by")
    private StaffUser curatedBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CampaignDifficulty> campaignDifficulties = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
