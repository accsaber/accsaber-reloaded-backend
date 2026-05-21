package com.accsaber.backend.controller.score;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.TooManyRequestsException;
import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.score.PluginSubmitRequest;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.score.ScoreService;
import com.accsaber.backend.service.score.SubmitNonceService;
import com.accsaber.backend.service.score.SubmitRateLimitService;
import com.accsaber.backend.util.PlatformScoreMapper;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Hidden
@RestController
@RequestMapping("/v1/submit")
@RequiredArgsConstructor
public class SubmitController {

    private final ScoreService scoreService;
    private final ModifierCacheService modifierCacheService;
    private final SubmitNonceService nonceService;
    private final SubmitRateLimitService rateLimitService;

    @PostMapping
    public ResponseEntity<ScoreResponse> submit(
            @Valid @RequestBody PluginSubmitRequest body,
            @RequestHeader(value = "X-Plugin-Build", required = false) String pluginBuild,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        if (pluginBuild == null || pluginBuild.isBlank()) {
            throw new ValidationException("X-Plugin-Build header is required");
        }
        validateModifiers(body.getModifierCodes());
        if (!nonceService.tryConsume(principal.getUserId(), body.getNonce())) {
            throw new ConflictException("Duplicate submission: nonce already used");
        }
        if (!rateLimitService.tryAcquire(principal.getUserId())) {
            throw new TooManyRequestsException("Submitting too fast; wait 60s between scores");
        }
        SubmitScoreRequest request = toServiceRequest(body, principal.getUserId());
        return ResponseEntity.ok(scoreService.submit(request));
    }

    private void validateModifiers(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        for (String code : codes) {
            if (code == null) continue;
            if (PlatformScoreMapper.BANNED_MODIFIER_CODES.contains(code.trim())) {
                throw new ValidationException("Banned modifier: " + code.trim());
            }
        }
    }

    private SubmitScoreRequest toServiceRequest(PluginSubmitRequest body, Long userId) {
        SubmitScoreRequest req = new SubmitScoreRequest();
        req.setUserId(userId);
        req.setMapDifficultyId(body.getMapDifficultyId());
        req.setScore(body.getScore());
        req.setScoreNoMods(body.getScoreNoMods());
        req.setRank(0);
        req.setRankWhenSet(0);
        req.setMaxCombo(body.getMaxCombo());
        req.setBadCuts(body.getBadCuts());
        req.setMisses(body.getMisses());
        req.setWallHits(body.getWallHits());
        req.setBombHits(body.getBombHits());
        req.setPauses(body.getPauses());
        req.setStreak115(body.getStreak115());
        req.setHmd(body.getHmd());
        req.setTimeSet(body.getTimeSet() != null ? body.getTimeSet() : Instant.now());
        req.setModifierIds(resolveModifierIds(body.getModifierCodes()));
        req.setPartial(body.isPartial());
        return req;
    }

    private List<UUID> resolveModifierIds(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, UUID> codeToId = modifierCacheService.getModifierCodeToId();
        List<UUID> ids = new ArrayList<>();
        for (String code : codes) {
            if (code == null) continue;
            UUID id = codeToId.get(code.trim());
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }
}
