package com.accsaber.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;
import com.accsaber.backend.repository.staff.StaffUserRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.staff.JwtService;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterStaffAccessTest {

    private static final String TOKEN = "staff-token";
    private static final UUID STAFF_ID = UUID.randomUUID();

    @Mock
    private JwtService jwtService;
    @Mock
    private StaffUserRepository staffUserRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeAcceptedStaffTokenAuthenticates() throws Exception {
        StaffUser staff = StaffUser.builder()
                .id(STAFF_ID)
                .username("ranker")
                .role(StaffRole.RANKING)
                .status(StaffUserStatus.ACCEPTED)
                .active(true)
                .build();
        stubStaffToken(Optional.of(staff));

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void staffTokenForInactiveOrUnacceptedAccountDoesNotAuthenticate() throws Exception {
        stubStaffToken(Optional.empty());

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void stubStaffToken(Optional<StaffUser> account) {
        when(jwtService.extractTokenType(TOKEN)).thenReturn(JwtService.TYPE_STAFF);
        when(jwtService.extractStaffId(TOKEN)).thenReturn(STAFF_ID);
        when(staffUserRepository.findByIdAndActiveTrueAndStatus(STAFF_ID, StaffUserStatus.ACCEPTED))
                .thenReturn(account);
    }

    private void runFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/v1/admin/maps");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
