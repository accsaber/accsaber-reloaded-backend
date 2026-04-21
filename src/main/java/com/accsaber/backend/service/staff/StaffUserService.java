package com.accsaber.backend.service.staff;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.staff.CreateStaffUserRequest;
import com.accsaber.backend.model.dto.request.staff.StaffAccessRequest;
import com.accsaber.backend.model.dto.request.staff.UpdateStaffProfileRequest;
import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.model.dto.response.staff.StaffUserResponse;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffUserService {

    private final StaffUserRepository staffUserRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void requestAccess(StaffAccessRequest request) {
        if (request.getUsername() == null && request.getEmail() == null) {
            throw new ValidationException("At least one of username or email is required");
        }
        if (request.getUsername() != null
                && staffUserRepository.findByUsernameAndRoleAndActiveTrue(
                        request.getUsername(), StaffRole.RANKING).isPresent()) {
            throw new ConflictException("Username already taken for this role: " + request.getUsername());
        }
        if (request.getEmail() != null
                && staffUserRepository.findByEmailAndActiveTrue(request.getEmail()).isPresent()) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }

        StaffUser staffUser = StaffUser.builder()
                .username(request.getUsername() != null ? request.getUsername()
                        : request.getEmail().split("@")[0])
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(StaffRole.RANKING)
                .status(StaffUserStatus.REQUESTED)
                .build();

        staffUserRepository.save(staffUser);
    }

    public Page<PublicStaffUserResponse> getAllPublic(Pageable pageable, Boolean active) {
        if (active == null) {
            return staffUserRepository.findAllByActiveTrueAndStatus(StaffUserStatus.ACCEPTED, pageable)
                    .map(this::toPublicResponse);
        }
        if (active) {
            return staffUserRepository.findAllByActiveTrueAndStatus(StaffUserStatus.ACCEPTED, pageable)
                    .map(this::toPublicResponse);
        }
        return staffUserRepository.findAllByActiveFalse(pageable)
                .map(this::toPublicResponse);
    }

    public PublicStaffUserResponse getByIdPublic(UUID id) {
        return staffUserRepository.findByIdAndActiveTrueAndStatus(id, StaffUserStatus.ACCEPTED)
                .map(this::toPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
    }

    public Page<StaffUserResponse> getAll(Pageable pageable) {
        return staffUserRepository.findAllByActiveTrue(pageable)
                .map(this::toResponse);
    }

    public Page<StaffUserResponse> getAllUnfiltered(StaffUserStatus status, Pageable pageable) {
        if (status != null) {
            return staffUserRepository.findAllByStatus(status, pageable)
                    .map(this::toResponse);
        }
        return staffUserRepository.findAll(pageable)
                .map(this::toResponse);
    }

    public StaffUserResponse getById(UUID id) {
        return staffUserRepository.findByIdAndActiveTrue(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
    }

    @Transactional
    public StaffUserResponse create(CreateStaffUserRequest request) {
        if (request.getUsername() == null && request.getEmail() == null) {
            throw new ValidationException("At least one of username or email is required");
        }
        if (request.getUsername() != null
                && staffUserRepository.findByUsernameAndRoleAndActiveTrue(
                        request.getUsername(), request.getRole()).isPresent()) {
            throw new ConflictException("Username already taken for this role: " + request.getUsername());
        }
        if (request.getEmail() != null
                && staffUserRepository.findByEmailAndActiveTrue(request.getEmail()).isPresent()) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }

        StaffUser.StaffUserBuilder builder = StaffUser.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .status(StaffUserStatus.ACCEPTED);

        User linkedUser = null;
        if (request.getUserId() != null) {
            linkedUser = userRepository.findByIdAndActiveTrue(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));
            builder.user(linkedUser);
        }

        builder.username(deriveUsername(request.getUsername(), linkedUser, request.getEmail()));

        return toResponse(staffUserRepository.save(builder.build()));
    }

    @Transactional
    public StaffUserResponse updateProfile(UUID staffId, UpdateStaffProfileRequest request) {
        if (request.getUsername() == null && request.getEmail() == null) {
            throw new ValidationException("At least one of username or email is required");
        }

        StaffUser staffUser = staffUserRepository.findByIdAndActiveTrue(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffId));

        if (request.getUsername() != null
                && !request.getUsername().equals(staffUser.getUsername())
                && staffUserRepository.findByUsernameAndRoleAndActiveTrue(
                        request.getUsername(), staffUser.getRole()).isPresent()) {
            throw new ConflictException("Username already taken for this role: " + request.getUsername());
        }
        if (request.getEmail() != null
                && !request.getEmail().equals(staffUser.getEmail())
                && staffUserRepository.findByEmailAndActiveTrue(request.getEmail()).isPresent()) {
            throw new ConflictException("Email already in use: " + request.getEmail());
        }

        if (request.getUsername() != null)
            staffUser.setUsername(request.getUsername());
        if (request.getEmail() != null)
            staffUser.setEmail(request.getEmail());

        return toResponse(staffUserRepository.save(staffUser));
    }

    private String deriveUsername(String explicit, User linkedUser, String email) {
        if (explicit != null)
            return explicit;
        if (linkedUser != null)
            return linkedUser.getName();
        if (email != null)
            return email.split("@")[0];
        return null;
    }

    @Transactional
    public StaffUserResponse linkUser(UUID staffId, Long userId) {
        StaffUser staffUser = staffUserRepository.findByIdAndActiveTrue(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + staffId));
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        staffUser.setUser(user);
        return toResponse(staffUserRepository.save(staffUser));
    }

    @Transactional
    public StaffUserResponse updateStatus(UUID id, StaffUserStatus status) {
        StaffUser staffUser = staffUserRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
        staffUser.setStatus(status);
        if (status != StaffUserStatus.ACCEPTED) {
            staffUser.setRefreshToken(null);
            staffUser.setTokenExpiresAt(null);
        }
        return toResponse(staffUserRepository.save(staffUser));
    }

    @Transactional
    public StaffUserResponse updateRole(UUID id, StaffRole role) {
        StaffUser staffUser = staffUserRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
        staffUser.setRole(role);
        return toResponse(staffUserRepository.save(staffUser));
    }

    @Transactional
    public void forceChangePassword(UUID id, String newPassword) {
        StaffUser staffUser = staffUserRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
        staffUser.setPassword(passwordEncoder.encode(newPassword));
        staffUser.setRefreshToken(null);
        staffUser.setTokenExpiresAt(null);
        staffUserRepository.save(staffUser);
    }

    @Transactional
    public void deactivate(UUID id) {
        StaffUser staffUser = staffUserRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
        staffUser.setActive(false);
        staffUser.setRefreshToken(null);
        staffUser.setTokenExpiresAt(null);
        staffUserRepository.save(staffUser);
    }

    private PublicStaffUserResponse toPublicResponse(StaffUser staffUser) {
        return PublicStaffUserResponse.builder()
                .id(staffUser.getId())
                .username(staffUser.getUsername())
                .role(staffUser.getRole())
                .userId(staffUser.getUser() != null ? String.valueOf(staffUser.getUser().getId()) : null)
                .avatarUrl(staffUser.getUser() != null ? staffUser.getUser().getAvatarUrl() : null)
                .active(staffUser.isActive())
                .build();
    }

    private StaffUserResponse toResponse(StaffUser staffUser) {
        return StaffUserResponse.builder()
                .id(staffUser.getId())
                .username(staffUser.getUsername())
                .email(staffUser.getEmail())
                .role(staffUser.getRole())
                .status(staffUser.getStatus())
                .userId(staffUser.getUser() != null ? String.valueOf(staffUser.getUser().getId()) : null)
                .active(staffUser.isActive())
                .createdAt(staffUser.getCreatedAt())
                .build();
    }
}
