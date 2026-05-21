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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.request.score.PluginSubmitRequest;
import com.accsaber.backend.model.dto.request.score.SubmitScoreRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.infra.ModifierCacheService;
import com.accsaber.backend.service.score.ScoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/submit")
@RequiredArgsConstructor
@Tag(name = "Plugin Submit")
public class SubmitController {

    private final ScoreService scoreService;
    private final ModifierCacheService modifierCacheService;

    @Operation(summary = "Submit a score from the in-game plugin", description = "Authenticated player-only. Inserts the score if no row matches (userId, mapDifficultyId, scoreNoMods) within the last 24h; otherwise backfills missing fields onto the existing row.")
    @PostMapping
    public ResponseEntity<ScoreResponse> submit(
            @Valid @RequestBody PluginSubmitRequest body,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        SubmitScoreRequest request = toServiceRequest(body, principal.getUserId());
        return ResponseEntity.ok(scoreService.submit(request));
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
