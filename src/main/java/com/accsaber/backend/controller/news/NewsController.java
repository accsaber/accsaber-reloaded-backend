package com.accsaber.backend.controller.news;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.news.PublicNewsResponse;
import com.accsaber.backend.model.entity.news.NewsType;
import com.accsaber.backend.service.news.NewsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/news")
@RequiredArgsConstructor
@Tag(name = "News")
public class NewsController {

    private final NewsService newsService;

    @Operation(summary = "List published news", description = "Pinned posts first, newest published first. Optional ?type filter (BATCH, CAMPAIGN, MILESTONE_SET, CURVE, GENERAL).")
    @GetMapping
    public ResponseEntity<Page<PublicNewsResponse>> list(
            @RequestParam(required = false) NewsType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(newsService.findPublic(type, pageable));
    }

    @Operation(summary = "Get a published news post by id")
    @GetMapping("/{id}")
    public ResponseEntity<PublicNewsResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(newsService.findPublicById(id));
    }

    @Operation(summary = "Get a published news post by slug")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<PublicNewsResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(newsService.findPublicBySlug(slug));
    }
}
