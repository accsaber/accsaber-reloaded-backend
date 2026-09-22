package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.controller.clan.ClanController;
import com.accsaber.backend.model.dto.request.clan.ClanCapacityRequest;
import com.accsaber.backend.model.dto.request.clan.ClanSeasonRequest;
import com.accsaber.backend.model.dto.request.clan.ClanSeasonRewardRequest;
import com.accsaber.backend.model.dto.request.clan.ClanWarModeRequest;
import com.accsaber.backend.model.dto.request.clan.ClanWarRewardItemRequest;
import com.accsaber.backend.model.dto.request.clan.ModerateClanRequest;
import com.accsaber.backend.model.dto.response.clan.ClanLevelStepResponse;
import com.accsaber.backend.model.dto.response.clan.ClanResponse;
import com.accsaber.backend.model.dto.response.clan.ClanSeasonResponse;
import com.accsaber.backend.model.dto.response.clan.ClanSeasonRewardResponse;
import com.accsaber.backend.model.dto.response.clan.ClanWarRewardItemResponse;
import com.accsaber.backend.model.entity.clan.ClanCapacity;
import com.accsaber.backend.model.entity.clan.ClanWarModeAxis;
import com.accsaber.backend.service.clan.ClanLevelService;
import com.accsaber.backend.service.clan.ClanSeasonService;
import com.accsaber.backend.service.clan.ClanService;
import com.accsaber.backend.service.clan.war.ClanWarRewardService;
import com.accsaber.backend.service.media.MediaProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/clans")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Clans")
public class AdminClanController {

    private final ClanService clanService;
    private final ClanLevelService levelService;
    private final ClanSeasonService seasonService;
    private final ClanWarRewardService warRewardService;
    private final MediaProcessingService mediaProcessingService;

    @Operation(summary = "Set how much of a capacity a level adds",
            description = "Capacities stack, so the amount here is added on top of every lower level's. Returns the "
                    + "whole level table.")
    @PutMapping("/levels/{level}/capacities/{capacity}")
    public ResponseEntity<List<ClanLevelStepResponse>> setCapacity(@PathVariable int level,
            @PathVariable ClanCapacity capacity, @Valid @RequestBody ClanCapacityRequest request) {
        return ResponseEntity.ok(levelService.setCapacity(level, capacity, request.getAmount()));
    }

    @Operation(summary = "Remove a capacity step from a level")
    @DeleteMapping("/levels/{level}/capacities/{capacity}")
    public ResponseEntity<List<ClanLevelStepResponse>> removeCapacity(@PathVariable int level,
            @PathVariable ClanCapacity capacity) {
        return ResponseEntity.ok(levelService.removeCapacity(level, capacity));
    }

    @Operation(summary = "Unlock an arena or ruleset at a level", description = "Axis is arena or ruleset.")
    @PutMapping("/war-modes/{axis}/{mode}")
    public ResponseEntity<List<ClanLevelStepResponse>> setWarMode(@PathVariable ClanWarModeAxis axis,
            @PathVariable String mode, @Valid @RequestBody ClanWarModeRequest request) {
        return ResponseEntity.ok(levelService.setWarMode(axis, mode, request.getLevel()));
    }

    @Operation(summary = "Lock an arena or ruleset away again")
    @DeleteMapping("/war-modes/{axis}/{mode}")
    public ResponseEntity<List<ClanLevelStepResponse>> removeWarMode(@PathVariable ClanWarModeAxis axis,
            @PathVariable String mode) {
        return ResponseEntity.ok(levelService.removeWarMode(axis, mode));
    }

    @Operation(summary = "Make a clan cosmetic a level reward",
            description = "Clans already at or past that level get the item straight away.")
    @PutMapping("/levels/{level}/items/{itemId}")
    public ResponseEntity<List<ClanLevelStepResponse>> setLevelItem(@PathVariable int level,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(levelService.setLevelItem(level, itemId));
    }

    @Operation(summary = "Stop a cosmetic being a level reward", description = "Clans that already own it keep it.")
    @DeleteMapping("/level-items/{itemId}")
    public ResponseEntity<List<ClanLevelStepResponse>> removeLevelItem(@PathVariable UUID itemId) {
        return ResponseEntity.ok(levelService.removeLevelItem(itemId));
    }

    @Operation(summary = "Create a clan season",
            description = "Seasons cannot overlap. The scheduler opens the next one on its own when none is running.")
    @PostMapping("/seasons")
    public ResponseEntity<ClanSeasonResponse> createSeason(@Valid @RequestBody ClanSeasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seasonService.create(request));
    }

    @Operation(summary = "Update a clan season", description = "A closed season cannot change.")
    @PatchMapping("/seasons/{seasonId}")
    public ResponseEntity<ClanSeasonResponse> updateSeason(@PathVariable UUID seasonId,
            @Valid @RequestBody ClanSeasonRequest request) {
        return ResponseEntity.ok(seasonService.update(seasonId, request));
    }

    @Operation(summary = "List a season's rewards")
    @GetMapping("/seasons/{seasonId}/rewards")
    public ResponseEntity<List<ClanSeasonRewardResponse>> seasonRewards(@PathVariable UUID seasonId) {
        return ResponseEntity.ok(seasonService.rewards(seasonId));
    }

    @Operation(summary = "Add a season reward",
            description = "Every clan ranked between rankFrom and rankTo when the season closes gets it. A clan "
                    + "cosmetic goes to the clan, anything else to its contributors in contribution order.")
    @PostMapping("/seasons/{seasonId}/rewards")
    public ResponseEntity<ClanSeasonRewardResponse> addSeasonReward(@PathVariable UUID seasonId,
            @Valid @RequestBody ClanSeasonRewardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seasonService.addReward(seasonId, request));
    }

    @Operation(summary = "Remove a season reward")
    @DeleteMapping("/seasons/rewards/{rewardId}")
    public ResponseEntity<Void> removeSeasonReward(@PathVariable UUID rewardId) {
        seasonService.removeReward(rewardId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List war reward items")
    @GetMapping("/war-rewards")
    public ResponseEntity<List<ClanWarRewardItemResponse>> warRewards() {
        return ResponseEntity.ok(warRewardService.list());
    }

    @Operation(summary = "Add a war reward item",
            description = "Paid to the winning side in contribution order, to everyone who contributed or only the top "
                    + "topContributors. A clan cosmetic goes to the winning clan instead.")
    @PostMapping("/war-rewards")
    public ResponseEntity<ClanWarRewardItemResponse> addWarReward(@Valid @RequestBody ClanWarRewardItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warRewardService.create(request));
    }

    @Operation(summary = "Update a war reward item")
    @PatchMapping("/war-rewards/{rewardId}")
    public ResponseEntity<ClanWarRewardItemResponse> updateWarReward(@PathVariable UUID rewardId,
            @Valid @RequestBody ClanWarRewardItemRequest request) {
        return ResponseEntity.ok(warRewardService.update(rewardId, request));
    }

    @Operation(summary = "Retire a war reward item")
    @DeleteMapping("/war-rewards/{rewardId}")
    public ResponseEntity<Void> retireWarReward(@PathVariable UUID rewardId) {
        warRewardService.deactivate(rewardId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Change a clan's name, tag, description or tag colour, or take its icon down",
            description = "For clans breaking the rules. Send removeIcon to clear an uploaded icon. The reason lands "
                    + "in the clan's audit log.")
    @PatchMapping("/{clanId}")
    public ResponseEntity<ClanResponse> moderate(@PathVariable UUID clanId,
            @Valid @RequestBody ModerateClanRequest request) {
        ClanResponse clan = clanService.moderate(clanId, request);
        if (request.isRemoveIcon()) {
            mediaProcessingService.deleteIfExists(ClanController.CLAN_ICON_SUBDIR, clanId.toString());
        }
        return ResponseEntity.ok(clan);
    }

    @Operation(summary = "Disband a clan",
            description = "Ends every membership, alliance, mission and war the clan had, and every member gets a server "
                    + "notification with the reason.")
    @DeleteMapping("/{clanId}")
    public ResponseEntity<Void> disband(@PathVariable UUID clanId, @RequestParam @NotBlank String reason) {
        clanService.disbandByStaff(clanId, reason);
        return ResponseEntity.noContent().build();
    }
}
