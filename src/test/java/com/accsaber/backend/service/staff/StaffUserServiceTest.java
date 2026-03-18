package com.accsaber.backend.service.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import com.accsaber.backend.model.dto.request.staff.OAuthLinkRequest;
import com.accsaber.backend.model.dto.request.staff.StaffAccessRequest;
import com.accsaber.backend.model.dto.response.staff.StaffOAuthLinkResponse;
import com.accsaber.backend.model.dto.response.staff.StaffUserResponse;
import com.accsaber.backend.model.entity.staff.StaffOAuthLink;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.repository.staff.StaffOAuthLinkRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class StaffUserServiceTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private StaffOAuthLinkRepository staffOAuthLinkRepository;

    @Mock
    private UserRepository userRepository;

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
    void linkOAuth_newProvider_savesLink() {
        StaffUser staffUser = buildStaffUser(StaffRole.RANKING);
        StaffUser linkedBy = buildStaffUser(StaffRole.ADMIN);
        UUID staffId = staffUser.getId();
        UUID linkedById = linkedBy.getId();

        OAuthLinkRequest request = new OAuthLinkRequest();
        request.setProvider("discord");
        request.setProviderUserId("123456789");
        request.setProviderUsername("TestUser#1234");

        StaffOAuthLink savedLink = StaffOAuthLink.builder()
                .id(UUID.randomUUID())
                .staffUser(staffUser)
                .provider("discord")
                .providerUserId("123456789")
                .providerUsername("TestUser#1234")
                .linkedBy(linkedBy)
                .linkedAt(Instant.now())
                .build();

        when(staffUserRepository.findByIdAndActiveTrue(staffId)).thenReturn(Optional.of(staffUser));
        when(staffUserRepository.findByIdAndActiveTrue(linkedById)).thenReturn(Optional.of(linkedBy));
        when(staffOAuthLinkRepository.findByProviderAndProviderUserId("discord", "123456789"))
                .thenReturn(Optional.empty());
        when(staffOAuthLinkRepository.save(any())).thenReturn(savedLink);

        StaffOAuthLinkResponse response = staffUserService.linkOAuth(staffId, request, linkedById);

        assertThat(response.getProvider()).isEqualTo("discord");
        assertThat(response.getProviderUsername()).isEqualTo("TestUser#1234");
    }

    @Test
    void linkOAuth_duplicateProvider_throwsConflict() {
        StaffUser staffUser = buildStaffUser(StaffRole.RANKING);
        StaffUser linkedBy = buildStaffUser(StaffRole.ADMIN);

        OAuthLinkRequest request = new OAuthLinkRequest();
        request.setProvider("discord");
        request.setProviderUserId("123456789");

        when(staffUserRepository.findByIdAndActiveTrue(staffUser.getId())).thenReturn(Optional.of(staffUser));
        when(staffUserRepository.findByIdAndActiveTrue(linkedBy.getId())).thenReturn(Optional.of(linkedBy));
        when(staffOAuthLinkRepository.findByProviderAndProviderUserId("discord", "123456789"))
                .thenReturn(Optional.of(StaffOAuthLink.builder().build()));

        assertThatThrownBy(() -> staffUserService.linkOAuth(staffUser.getId(), request, linkedBy.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void unlinkOAuth_existingLink_deletesLink() {
        UUID linkId = UUID.randomUUID();
        StaffOAuthLink link = StaffOAuthLink.builder().id(linkId).build();

        when(staffOAuthLinkRepository.findById(linkId)).thenReturn(Optional.of(link));

        staffUserService.unlinkOAuth(linkId);

        verify(staffOAuthLinkRepository).delete(link);
    }

    @Test
    void requestAccess_withUsername_savesWithRequestedStatus() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setUsername("newranker");
        request.setPassword("password123");

        when(staffUserRepository.findByUsernameAndRoleAndActiveTrue("newranker", StaffRole.RANKING))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(staffUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        staffUserService.requestAccess(request);

        ArgumentCaptor<StaffUser> captor = ArgumentCaptor.forClass(StaffUser.class);
        verify(staffUserRepository).save(captor.capture());
        StaffUser saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(StaffUserStatus.REQUESTED);
        assertThat(saved.getRole()).isEqualTo(StaffRole.RANKING);
        assertThat(saved.getUsername()).isEqualTo("newranker");
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
    }

    @Test
    void requestAccess_withEmailOnly_derivesUsername() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setEmail("ranker@example.com");
        request.setPassword("password123");

        when(staffUserRepository.findByEmailAndActiveTrue("ranker@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(staffUserRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        staffUserService.requestAccess(request);

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

        assertThatThrownBy(() -> staffUserService.requestAccess(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("username or email");
    }

    @Test
    void requestAccess_duplicateUsername_throwsConflict() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setUsername("existing");
        request.setPassword("password123");

        when(staffUserRepository.findByUsernameAndRoleAndActiveTrue("existing", StaffRole.RANKING))
                .thenReturn(Optional.of(buildStaffUser(StaffRole.RANKING)));

        assertThatThrownBy(() -> staffUserService.requestAccess(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requestAccess_duplicateEmail_throwsConflict() {
        StaffAccessRequest request = new StaffAccessRequest();
        request.setEmail("taken@example.com");
        request.setPassword("password123");

        when(staffUserRepository.findByEmailAndActiveTrue("taken@example.com"))
                .thenReturn(Optional.of(buildStaffUser(StaffRole.RANKING)));

        assertThatThrownBy(() -> staffUserService.requestAccess(request))
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
