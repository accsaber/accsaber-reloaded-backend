package com.accsaber.backend.controller.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.player.ApToNextResponse;
import com.accsaber.backend.model.dto.response.player.SkillResponse;
import com.accsaber.backend.service.skill.SkillService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "Players")
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "A player's skill levels", description = "0 to 100 per category from rank, sustain and "
            + "best play, given combined and per part. Every category unless you pass one.")
    @GetMapping("/{userId}/skill")
    public ResponseEntity<SkillResponse> getSkill(
            @PathVariable Long userId,
            @Parameter(description = "Category code, leave out for all") @RequestParam(required = false) String category) {
        return ResponseEntity.ok(skillService.computeSkillForUser(userId, category));
    }

    @Operation(summary = "AP needed for one more AP", description = "The raw AP one new play "
            + "needs to raise the weighted total by exactly one.")
    @GetMapping("/{userId}/categories/{category}/ap-to-next")
    public ResponseEntity<ApToNextResponse> getApToNext(
            @PathVariable Long userId,
            @PathVariable String category) {
        return ResponseEntity.ok(skillService.calculateApToNext(userId, category));
    }
}
