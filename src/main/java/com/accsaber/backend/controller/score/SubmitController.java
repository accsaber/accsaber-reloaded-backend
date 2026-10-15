package com.accsaber.backend.controller.score;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.score.PluginSubmitRequest;
import com.accsaber.backend.model.dto.response.score.ScoreResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.score.ScoreService;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Hidden
@RestController
@RequestMapping("/v1/submit")
@RequiredArgsConstructor
public class SubmitController {

    private final ScoreService scoreService;

    @PostMapping
    public ResponseEntity<ScoreResponse> submit(
            @Valid @RequestBody PluginSubmitRequest body,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return scoreService.submitFromPlugin(body, principal)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
