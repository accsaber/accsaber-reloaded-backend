package com.accsaber.backend.repository.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    List<StaffUser> findByUsernameAndActiveTrue(String username);

    Optional<StaffUser> findByUsernameAndRoleAndActiveTrue(String username, StaffRole role);

    Optional<StaffUser> findByEmailAndActiveTrue(String email);

    Optional<StaffUser> findByRefreshToken(String refreshToken);

    Optional<StaffUser> findByIdAndActiveTrue(UUID id);

    List<StaffUser> findAllByActiveTrue();

    Page<StaffUser> findAllByActiveTrue(Pageable pageable);

    Page<StaffUser> findAllByActiveFalse(Pageable pageable);

    Page<StaffUser> findAllByActiveTrueAndStatus(StaffUserStatus status, Pageable pageable);

    Optional<StaffUser> findByIdAndActiveTrueAndStatus(UUID id, StaffUserStatus status);
}
