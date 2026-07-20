package com.accsaber.backend.model.entity.item;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_item_links")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserItemLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_item_link_modifiers",
            joinColumns = @JoinColumn(name = "user_item_link_id"),
            inverseJoinColumns = @JoinColumn(name = "modifier_id"))
    @Builder.Default
    private Set<ItemModifier> modifiers = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unusual_effect_id")
    private UnusualEffect unusualEffect;

    @Column(name = "serial_number")
    private Long serialNumber;

    @Column(nullable = false)
    @Builder.Default
    private Long quantity = 1L;

    @Column(nullable = false)
    @Builder.Default
    private boolean escrowed = false;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ItemSource source;

    @Column(name = "source_id")
    private String sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "awarded_by")
    private StaffUser awardedBy;

    @Column(name = "awarded_at", nullable = false)
    @Builder.Default
    private Instant awardedAt = Instant.now();

    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
