package com.accsaber.backend.controller.ranking;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.news.CreateNewsRequest;
import com.accsaber.backend.model.dto.request.news.UpdateNewsRequest;
import com.accsaber.backend.model.dto.response.news.NewsResponse;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.news.NewsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/ranking/news")
@PreAuthorize("hasRole('RANKING_HEAD')")
@RequiredArgsConstructor
@Tag(name = "Ranking - News")
public class RankingNewsController {

    private final NewsService newsService;

    @Operation(summary = "List news authored by the current staff user")
    @GetMapping("/mine")
    public ResponseEntity<Page<NewsResponse>> listMine(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(newsService.findStaffByAuthor(StaffPrincipals.staffIdOf(authentication), pageable));
    }

    @Operation(summary = "Get a news post (any status) by id")
    @GetMapping("/{id}")
    public ResponseEntity<NewsResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(newsService.findStaffById(id));
    }

    @Operation(summary = "Create a news post", description = "The authenticated staff user becomes the author/owner")
    @PostMapping
    public ResponseEntity<NewsResponse> create(
            @Valid @RequestBody CreateNewsRequest request,
            Authentication authentication) {
        NewsResponse response = newsService.create(request, StaffPrincipals.staffIdOf(authentication));
        return ResponseEntity.created(URI.create("/v1/news/" + response.getId())).body(response);
    }

    @Operation(summary = "Update a news post", description = "Only the author can update; admins use the admin endpoint")
    @PatchMapping("/{id}")
    public ResponseEntity<NewsResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNewsRequest request,
            Authentication authentication) {
        NewsResponse response = newsService.update(
                id,
                request,
                StaffPrincipals.staffIdOf(authentication),
                StaffPrincipals.roleOf(authentication));
        return ResponseEntity.ok(response);
    }
}
