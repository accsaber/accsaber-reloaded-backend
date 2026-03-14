package com.accsaber.backend.repository.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.staff.StaffOAuthLink;

public interface StaffOAuthLinkRepository extends JpaRepository<StaffOAuthLink, UUID> {

    List<StaffOAuthLink> findByStaffUserId(UUID staffUserId);

    Optional<StaffOAuthLink> findByProviderAndProviderUserId(String provider, String providerUserId);
}
