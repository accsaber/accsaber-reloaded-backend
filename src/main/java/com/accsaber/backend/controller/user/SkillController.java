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
@Tag(name = "Skill")
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "Get skill levels for a user", description = "Computes a 0-100 skill level per category, broken into rank, sustained (+1 raw equivalent), and peak (top AP) components. "
            + "Returns all categories by default; pass `category` to filter to one.")
    @GetMapping("/{userId}/skill")
    public ResponseEntity<SkillResponse> getSkill(
            @PathVariable Long userId,
            @Parameter(description = "Optional category code; omit for all categories") @RequestParam(required = false) String category) {
        return ResponseEntity.ok(skillService.computeSkillForUser(userId, category));
    }

    @Operation(summary = "Get the raw AP needed to gain 1 weighted AP in a category", description = "Returns the raw AP value of a hypothetical play that would raise the user's category weighted total by exactly 1.")
    @GetMapping("/{userId}/categories/{category}/ap-to-next")
    public ResponseEntity<ApToNextResponse> getApToNext(
            @PathVariable Long userId,
            @PathVariable String category) {
        return ResponseEntity.ok(skillService.calculateApToNext(userId, category));
    }
}
