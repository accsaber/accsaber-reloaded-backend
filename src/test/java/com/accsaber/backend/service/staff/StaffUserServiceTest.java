package com.accsaber.backend.service.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.staff.CreateStaffUserRequest;
import com.accsaber.backend.model.dto.request.staff.StaffAccessRequest;
import com.accsaber.backend.model.dto.response.staff.StaffUserResponse;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.OauthSessionRepository;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class StaffUserServiceTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OauthSessionRepository oauthSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private StaffUserService staffUserService;

    @Test
    void create_newUsername_savesWithHashedPassword() {
        CreateStaffUserRequest request = new CreateStaffUserRequest();
        request.setUsername("newstaff");
        request.setPassword("password123");
        request.setRole(StaffRole.RANKING);

        StaffUser saved = buildStaffUser(StaffRole.RANKING);

        when(staffUserRepository.findByUsernameAndRoleAndActiveTrue("newstaff", StaffRole.RANKING))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(staffUserRepository.save(any())).thenReturn(saved);

        StaffUserResponse response = staffUserService.create(request);

        assertThat(response).isNotNull();
        verify(passwordEncoder).encode("password123");
        verify(staffUserRepository).save(any(StaffUser.class));
    }

    @Test
    void create_duplicateUsername_throwsConflict() {
        CreateStaffUserRequest request = new CreateStaffUserRequest();
        request.setUsername("existing");
        request.setPassword("password");
        request.setRole(StaffRole.RANKING);

        when(staffUserRepository.findByUsernameAndRoleAndActiveTrue("existing", StaffRole.RANKING))
                .thenReturn(Optional.of(buildStaffUser(StaffRole.RANKING)));

        assertThatThrownBy(() -> staffUserService.create(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateRole_existingStaff_updatesRole() {
        StaffUser staffUser = buildStaffUser(StaffRole.RANKING);
        UUID staffId = staffUser.getId();

        when(staffUserRepository.findByIdAndActiveTrue(staffId)).thenReturn(Optional.of(staffUser));
        when(staffUserRepository.save(any())).thenReturn(staffUser);

        staffUserService.updateRole(staffId, StaffRole.RANKING_HEAD);

        verify(staffUserRepository).save(staffUser);
        assertThat(staffUser.getRole()).isEqualTo(StaffRole.RANKING_HEAD);
    }

    @Test
    void deactivate_existingStaff_clearsTokensAndSetsInactive() {
        StaffUser staffUser = buildStaffUser(StaffRole.RANKING);
        staffUser.setRefreshToken("some-token");
        UUID staffId = staffUser.getId();

        when(staffUserRepository.findByIdAndActiveTrue(staffId)).thenReturn(Optional.of(staffUser));
        when(staffUserRepository.save(any())).thenReturn(staffUser);

        staffUserService.deactivate(staffId);

        assertThat(staffUser.isActive()).isFalse();
        assertThat(staffUser.getRefreshToken()).isNull();
        assertThat(staffUser.getTokenExpiresAt()).isNull();
    }

    @Test
    void requestAccess_withUsername_savesWithRequestedStatus() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setUsername("newranker");
        request.setPassword("password123");

        User user = User.builder().id(7L).name("Player").active(true).build();
        when(staffUserRepository.existsByUserIdAndActiveTrue(7L)).thenReturn(false);
        when(staffUserRepository.findByUsernameAndRoleAndActiveTrue("newranker", StaffRole.RANKING))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(staffUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        staffUserService.requestAccess(request, 7L);

        ArgumentCaptor<StaffUser> captor = ArgumentCaptor.forClass(StaffUser.class);
        verify(staffUserRepository).save(captor.capture());
        StaffUser saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(StaffUserStatus.REQUESTED);
        assertThat(saved.getRole()).isEqualTo(StaffRole.RANKING);
        assertThat(saved.getUsername()).isEqualTo("newranker");
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.getUser()).isEqualTo(user);
    }

    @Test
    void requestAccess_withEmailOnly_derivesUsername() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setEmail("ranker@example.com");
        request.setPassword("password123");

        User user = User.builder().id(7L).name("Player").active(true).build();
        when(staffUserRepository.existsByUserIdAndActiveTrue(7L)).thenReturn(false);
        when(staffUserRepository.findByEmailAndActiveTrue("ranker@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByIdAndActiveTrue(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(staffUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        staffUserService.requestAccess(request, 7L);

        ArgumentCaptor<StaffUser> captor = ArgumentCaptor.forClass(StaffUser.class);
        verify(staffUserRepository).save(captor.capture());
        StaffUser saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("ranker");
        assertThat(saved.getEmail()).isEqualTo("ranker@example.com");
        assertThat(saved.getStatus()).isEqualTo(StaffUserStatus.REQUESTED);
    }

    @Test
    void requestAccess_noUsernameOrEmail_throwsValidation() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setPassword("password123");

        assertThatThrownBy(() -> staffUserService.requestAccess(request, 7L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("username or email");
    }

    @Test
    void requestAccess_duplicateUsername_throwsConflict() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setUsername("existing");
        request.setPassword("password123");

        when(staffUserRepository.existsByUserIdAndActiveTrue(7L)).thenReturn(false);
        when(staffUserRepository.findByUsernameAndRoleAndActiveTrue("existing", StaffRole.RANKING))
                .thenReturn(Optional.of(buildStaffUser(StaffRole.RANKING)));

        assertThatThrownBy(() -> staffUserService.requestAccess(request, 7L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requestAccess_duplicateEmail_throwsConflict() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setEmail("taken@example.com");
        request.setPassword("password123");

        when(staffUserRepository.existsByUserIdAndActiveTrue(7L)).thenReturn(false);
        when(staffUserRepository.findByEmailAndActiveTrue("taken@example.com"))
                .thenReturn(Optional.of(buildStaffUser(StaffRole.RANKING)));

        assertThatThrownBy(() -> staffUserService.requestAccess(request, 7L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requestAccess_existingStaffForUser_throwsConflict() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setUsername("newranker");
        request.setPassword("password123");

        when(staffUserRepository.existsByUserIdAndActiveTrue(7L)).thenReturn(true);

        assertThatThrownBy(() -> staffUserService.requestAccess(request, 7L))
                .isInstanceOf(ConflictException.class);
    }

    private StaffUser buildStaffUser(StaffRole role) {
        return StaffUser.builder()
                .id(UUID.randomUUID())
                .username("staff-" + UUID.randomUUID())
                .password("hashed")
                .role(role)
                .active(true)
                .build();
    }
}
