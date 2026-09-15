package com.accsaber.backend.service.staff;

import java.util.Objects;
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
import com.accsaber.backend.repository.user.OauthSessionRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffUserService {

    private final StaffUserRepository staffUserRepository;
    private final UserRepository userRepository;
    private final OauthSessionRepository oauthSessionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void requestAccess(StaffAccessRequest request, Long userId) {
        if (request.getUsername() == null && request.getEmail() == null) {
            throw new ValidationException("At least one of username or email is required");
        }
        if (staffUserRepository.existsByUserIdAndActiveTrue(userId)) {
            throw new ConflictException("This account already has staff access or a pending request");
        }
        assertIdentifiersFree(request.getUsername(), request.getEmail(), StaffRole.RANKING);

        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        StaffUser staffUser = StaffUser.builder()
                .username(request.getUsername() != null ? request.getUsername()
                        : request.getEmail().split("@")[0])
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(StaffRole.RANKING)
                .status(StaffUserStatus.REQUESTED)
                .user(user)
                .build();

        staffUserRepository.save(staffUser);
    }

    public Page<PublicStaffUserResponse> getAllPublic(Pageable pageable, Boolean active) {
        if (active == null) {
            return staffUserRepository.findAllByActiveTrueAndStatus(StaffUserStatus.ACCEPTED, pageable)
                    .map(StaffMapper::toPublicResponse);
        }
        if (active) {
            return staffUserRepository.findAllByActiveTrueAndStatus(StaffUserStatus.ACCEPTED, pageable)
                    .map(StaffMapper::toPublicResponse);
        }
        return staffUserRepository.findAllByActiveFalse(pageable)
                .map(StaffMapper::toPublicResponse);
    }

    public PublicStaffUserResponse getByIdPublic(UUID id) {
        return staffUserRepository.findByIdAndActiveTrueAndStatus(id, StaffUserStatus.ACCEPTED)
                .map(StaffMapper::toPublicResponse)
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
        return toResponse(requireActive(id));
    }

    @Transactional
    public StaffUserResponse create(CreateStaffUserRequest request) {
        if (request.getUsername() == null && request.getEmail() == null) {
            throw new ValidationException("At least one of username or email is required");
        }
        assertIdentifiersFree(request.getUsername(), request.getEmail(), request.getRole());

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

        StaffUser staffUser = requireActive(staffId);
        assertIdentifiersFree(
                Objects.equals(request.getUsername(), staffUser.getUsername()) ? null : request.getUsername(),
                Objects.equals(request.getEmail(), staffUser.getEmail()) ? null : request.getEmail(),
                staffUser.getRole());

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
        StaffUser staffUser = requireActive(staffId);
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        staffUser.setUser(user);
        return toResponse(staffUserRepository.save(staffUser));
    }

    @Transactional
    public StaffUserResponse updateStatus(UUID id, StaffUserStatus status) {
        StaffUser staffUser = requireActive(id);
        staffUser.setStatus(status);
        if (status != StaffUserStatus.ACCEPTED) {
            clearTokens(staffUser);
        }
        invalidateLinkedPlayerSessions(staffUser);
        return toResponse(staffUserRepository.save(staffUser));
    }

    @Transactional
    public StaffUserResponse updateRole(UUID id, StaffRole role) {
        StaffUser staffUser = requireActive(id);
        staffUser.setRole(role);
        invalidateLinkedPlayerSessions(staffUser);
        return toResponse(staffUserRepository.save(staffUser));
    }

    @Transactional
    public void forceChangePassword(UUID id, String newPassword) {
        StaffUser staffUser = requireActive(id);
        staffUser.setPassword(passwordEncoder.encode(newPassword));
        clearTokens(staffUser);
        staffUserRepository.save(staffUser);
    }

    @Transactional
    public StaffUserResponse setActive(UUID id, boolean active) {
        StaffUser staffUser = requireExisting(id);
        if (staffUser.isActive() == active) {
            return toResponse(staffUser);
        }
        if (active) {
            assertIdentifiersFree(staffUser.getUsername(), staffUser.getEmail(), staffUser.getRole());
        } else {
            clearTokens(staffUser);
            invalidateLinkedPlayerSessions(staffUser);
        }
        staffUser.setActive(active);
        return toResponse(staffUserRepository.save(staffUser));
    }

    @Transactional
    public void delete(UUID id) {
        StaffUser staffUser = requireExisting(id);
        if (staffUserRepository.hasAuthoredRecords(id)) {
            throw new ConflictException(
                    "Staff user " + id + " authored news posts or admin actions, deactivate them instead");
        }
        staffUserRepository.detachStaffReferences(id);
        invalidateLinkedPlayerSessions(staffUser);
        staffUserRepository.delete(staffUser);
    }

    private StaffUser requireActive(UUID id) {
        return staffUserRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
    }

    private StaffUser requireExisting(UUID id) {
        return staffUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found: " + id));
    }

    private void assertIdentifiersFree(String username, String email, StaffRole role) {
        if (username != null && staffUserRepository.findByUsernameAndRoleAndActiveTrue(username, role).isPresent()) {
            throw new ConflictException("Username already taken for this role: " + username);
        }
        if (email != null && staffUserRepository.findByEmailAndActiveTrue(email).isPresent()) {
            throw new ConflictException("Email already in use: " + email);
        }
    }

    private void clearTokens(StaffUser staffUser) {
        staffUser.setRefreshToken(null);
        staffUser.setTokenExpiresAt(null);
    }

    private void invalidateLinkedPlayerSessions(StaffUser staffUser) {
        if (staffUser.getUser() != null) {
            oauthSessionRepository.deleteByUserId(staffUser.getUser().getId());
        }
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
