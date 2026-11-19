package com.accsaber.backend.controller.map;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.chat.SendChatMessageRequest;
import com.accsaber.backend.model.dto.request.map.CriteriaCheckRequest;
import com.accsaber.backend.model.dto.request.map.MapHubLookup;
import com.accsaber.backend.model.dto.request.map.SuggestMapRequest;
import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.dto.response.map.AutoCriteriaCheckResponse;
import com.accsaber.backend.model.dto.response.map.MapHubResponse;
import com.accsaber.backend.model.dto.response.map.PublicMapDifficultyResponse;
import com.accsaber.backend.model.dto.response.map.VoteListResponse;
import com.accsaber.backend.model.entity.map.Difficulty;
import com.accsaber.backend.security.PlayerUserDetails;
import com.accsaber.backend.service.chat.ChatService;
import com.accsaber.backend.service.map.AutoCriteriaService;
import com.accsaber.backend.service.map.MapChatChannel;
import com.accsaber.backend.service.map.MapHubService;
import com.accsaber.backend.service.map.MapSubscriptionService;
import com.accsaber.backend.service.map.MapSuggestionService;
import com.accsaber.backend.service.map.MapVotingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/maps")
@RequiredArgsConstructor
@Tag(name = "Ranking hub")
public class MapHubController {

    private final MapSuggestionService mapSuggestionService;
    private final MapVotingService mapVotingService;
    private final AutoCriteriaService autoCriteriaService;
    private final ChatService chatService;
    private final MapChatChannel mapChatChannel;
    private final MapSubscriptionService mapSubscriptionService;
    private final MapHubService mapHubService;

    @Operation(summary = "Suggest a map", description = "Needs a BeatLeader leaderboard ID and a category. "
            + "Five open suggestions per player.")
    @PostMapping("/suggestions")
    public ResponseEntity<PublicMapDifficultyResponse> suggest(
            @Valid @RequestBody SuggestMapRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                mapSuggestionService.suggest(PlayerUserDetails.require(principal).getUserId(), request));
    }

    @Operation(summary = "Withdraw your own suggestion", description = "Only while it is still a suggestion. It "
            + "leaves every list and the same leaderboard can be suggested again.")
    @DeleteMapping("/suggestions/{difficultyId}")
    public ResponseEntity<Void> withdraw(
            @PathVariable UUID difficultyId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        mapSuggestionService.withdraw(difficultyId, PlayerUserDetails.require(principal).getUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Everything the hub page of a difficulty shows", description = "The difficulty, its rank "
            + "votes, the first page of comments and, with a player token, whether you follow it.")
    @GetMapping("/difficulties/{difficultyId}/hub")
    public ResponseEntity<MapHubResponse<PublicMapDifficultyResponse>> hub(
            @PathVariable UUID difficultyId,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable comments) {
        return ResponseEntity.ok(mapHubService.detail(difficultyId,
                principal == null ? null : principal.getUserId(), comments));
    }

    @Operation(summary = "Hub page of a difficulty by BeatSaver code", description = "Picks the suggested, queued "
            + "or qualified difficulty first, then the ranked one, then the most recently rejected.")
    @GetMapping("/difficulties/by-code/{beatsaverCode}/hub")
    public ResponseEntity<MapHubResponse<PublicMapDifficultyResponse>> hubByCode(
            @PathVariable String beatsaverCode,
            @RequestParam Difficulty difficulty,
            @RequestParam(required = false) String characteristic,
            @AuthenticationPrincipal PlayerUserDetails principal,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable comments) {
        return ResponseEntity.ok(mapHubService.detailByCode(
                new MapHubLookup(beatsaverCode, difficulty, characteristic),
                principal == null ? null : principal.getUserId(), comments));
    }

    @Operation(summary = "Rank votes on a difficulty", description = "No voter names and no complexity, "
            + "oldest update first.")
    @GetMapping("/difficulties/{difficultyId}/votes")
    public ResponseEntity<VoteListResponse> listVotes(@PathVariable UUID difficultyId) {
        return ResponseEntity.ok(mapVotingService.getAnonymousRankVotes(difficultyId));
    }

    @Operation(summary = "Comments on a difficulty", description = "Newest first.")
    @GetMapping("/difficulties/{difficultyId}/comments")
    public ResponseEntity<Page<ChatMessageResponse>> listComments(
            @PathVariable UUID difficultyId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chatService.getTranscript(mapChatChannel, difficultyId, pageable));
    }

    @Operation(summary = "Comment on a difficulty", description = "Rate limited.")
    @PostMapping("/difficulties/{difficultyId}/comments")
    public ResponseEntity<ChatMessageResponse> comment(
            @PathVariable UUID difficultyId,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.sendMessage(mapChatChannel, difficultyId,
                PlayerUserDetails.require(principal).getUserId(), request.getContent()));
    }

    @Operation(summary = "Follow a difficulty", description = "Notifies on new comments, status changes "
            + "and rank votes. Suggesting a map follows it already.")
    @PutMapping("/difficulties/{difficultyId}/subscription")
    public ResponseEntity<Void> subscribe(
            @PathVariable UUID difficultyId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        mapSubscriptionService.subscribe(difficultyId, PlayerUserDetails.require(principal).getUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unfollow a difficulty")
    @DeleteMapping("/difficulties/{difficultyId}/subscription")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable UUID difficultyId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        mapSubscriptionService.unsubscribe(difficultyId, PlayerUserDetails.require(principal).getUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Check a BeatSaver map against the criteria", description = "By song hash, up to ten "
            + "difficulties in one call. Nothing is stored on the map. One call is one of five checks per ten "
            + "minutes, shared with the refresh below.")
    @PostMapping(value = "/criteria-check", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<AutoCriteriaCheckResponse>> check(
            @Valid @RequestBody CriteriaCheckRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(autoCriteriaService.check(
                PlayerUserDetails.require(principal).getUserId(), request, null));
    }

    @Operation(summary = "Check an uploaded map zip against the criteria", description = "For maps that are not "
            + "on BeatSaver. Same request as a JSON part named request, the hash is ignored.")
    @PostMapping(value = "/criteria-check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AutoCriteriaCheckResponse>> checkUpload(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("request") CriteriaCheckRequest request,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(autoCriteriaService.check(
                PlayerUserDetails.require(principal).getUserId(), request, file));
    }

    @Operation(summary = "Refresh the criteria check on your own suggestion")
    @PostMapping("/difficulties/{difficultyId}/criteria-check")
    public ResponseEntity<AutoCriteriaCheckResponse> refreshCriteriaCheck(
            @PathVariable UUID difficultyId,
            @AuthenticationPrincipal PlayerUserDetails principal) {
        return ResponseEntity.ok(autoCriteriaService.runCheckForImporter(difficultyId,
                PlayerUserDetails.require(principal).getUserId()));
    }
}
