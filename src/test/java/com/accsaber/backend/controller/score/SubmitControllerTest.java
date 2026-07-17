package com.accsaber.backend.controller.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.service.staff.JwtService;
import com.accsaber.backend.model.dto.request.score.PluginSubmitRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.score.ScoreService;
import com.accsaber.backend.service.score.SubmitNonceService;
import com.accsaber.backend.service.score.SubmitRateLimitService;

@ExtendWith(MockitoExtension.class)
class SubmitControllerTest {

    private static final Long USER_ID = 76561198000000123L;

    @Mock
    private ScoreService scoreService;

    @Mock
    private ModifierCacheService modifierCacheService;

    private SubmitController controller;
    private PlayerUserDetails principal;

    @BeforeEach
    void setUp() {
        SubmitNonceService nonceService = new SubmitNonceService();
        SubmitRateLimitService rateLimitService = new SubmitRateLimitService();
        controller = new SubmitController(scoreService, modifierCacheService, nonceService, rateLimitService);
        User user = User.builder().id(USER_ID).name("Player").active(true).banned(false).build();
        principal = new PlayerUserDetails(user);
    }

    private PluginSubmitRequest baseRequest() {
        PluginSubmitRequest r = new PluginSubmitRequest();
        r.setNonce(UUID.randomUUID().toString());
        r.setMapDifficultyId(UUID.randomUUID());
        r.setScore(900_000);
        r.setScoreNoMods(900_000);
        r.setTimeSet(Instant.now());
        return r;
    }

    @Test
    void rejectsWhenPrincipalIsNull() {
        assertThatThrownBy(() -> controller.submit(baseRequest(), null))
                .isInstanceOf(UnauthorizedException.class);
        verify(scoreService, never()).submit(any());
    }

    @Test
    void rejectsWebScopeToken() {
        User user = User.builder().id(USER_ID).name("Player").active(true).banned(false).build();
        PlayerUserDetails webPrincipal = new PlayerUserDetails(user, null, null, JwtService.SCOPE_WEB);
        assertThatThrownBy(() -> controller.submit(baseRequest(), webPrincipal))
                .isInstanceOf(ForbiddenException.class);
        verify(scoreService, never()).submit(any());
    }

    @Test
    void rejectsWhenModifierIsBanned() {
        PluginSubmitRequest r = baseRequest();
        r.setModifierCodes(List.of("NF", "NO"));
        assertThatThrownBy(() -> controller.submit(r, principal))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Banned modifier");
        verify(scoreService, never()).submitPlayer(any());
    }

    @Test
    void rejectsDuplicateNonce() {
        PluginSubmitRequest r = baseRequest();
        when(scoreService.submitPlayer(any())).thenReturn(ScoreResponse.builder().build());

        controller.submit(r, principal);

        assertThatThrownBy(() -> controller.submit(r, principal))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    void rejectsSecondSubmissionWithinRateLimitWindow() {
        when(scoreService.submitPlayer(any())).thenReturn(ScoreResponse.builder().build());

        controller.submit(baseRequest(), principal);

        PluginSubmitRequest second = baseRequest();
        assertThatThrownBy(() -> controller.submit(second, principal))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("60s");
    }

    @Test
    void acceptsHappyPath_passesUserIdFromPrincipal() {
        PluginSubmitRequest r = baseRequest();
        when(scoreService.submitPlayer(any())).thenAnswer(inv -> {
            var req = (com.accsaber.backend.model.dto.request.score.SubmitScoreRequest) inv.getArgument(0);
            assertThat(req.getUserId()).isEqualTo(USER_ID);
            return ScoreResponse.builder().build();
        });

        var response = controller.submit(r, principal);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
