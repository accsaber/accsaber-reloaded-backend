package com.accsaber.backend.model.entity.staff;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "staff_oauth_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffOAuthLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_user_id", nullable = false)
    private StaffUser staffUser;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_username", length = 255)
    private String providerUsername;

    @Column(name = "provider_avatar_url")
    private String providerAvatarUrl;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by_staff_id", nullable = false)
    private StaffUser linkedBy;
}
