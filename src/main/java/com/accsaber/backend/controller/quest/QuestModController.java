package com.accsaber.backend.controller.quest;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.UnauthorizedException;
import com.accsaber.backend.model.dto.response.quest.QuestReleaseResponse;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.quest.QuestModService;
import com.accsaber.backend.service.quest.QuestModService.GeneratedMod;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/quest")
@RequiredArgsConstructor
@Tag(name = "Quest")
public class QuestModController {

    private final QuestModService questModService;

    @Operation(summary = "Quest mod releases", description = "Each has the Beat Saber version it was built for.")
    @GetMapping("/releases")
    public ResponseEntity<List<QuestReleaseResponse>> listReleases() {
        return ResponseEntity.ok(questModService.listReleases());
    }

    @Operation(summary = "Build your Quest mod", description = "Comes with your session baked in. It "
            + "has a private credential, never share or cache it. Leave out the tag for the latest.")
    @PostMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam(required = false) String tag,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        if (principal == null) {
            throw new UnauthorizedException("Player authentication required");
        }
        GeneratedMod mod = questModService.generate(principal.getUserId(), tag);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + mod.fileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(mod.bytes());
    }
}
