package com.accsaber.backend.controller.admin;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.news.CreateNewsRequest;
import com.accsaber.backend.model.dto.request.news.UpdateNewsRequest;
import com.accsaber.backend.model.dto.response.news.NewsResponse;
import com.accsaber.backend.model.entity.news.NewsStatus;
import com.accsaber.backend.model.entity.news.NewsType;
import com.accsaber.backend.security.StaffPrincipals;
import com.accsaber.backend.service.media.MediaProcessingService;
import com.accsaber.backend.service.news.NewsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/news")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin News")
public class AdminNewsController {

    private static final String NEWS_IMAGE_SUBDIR = "news";

    private final NewsService newsService;
    private final MediaProcessingService mediaProcessingService;

    @Operation(summary = "List all active news", description = "Optional ?status and ?type filters")
    @GetMapping
    public ResponseEntity<Page<NewsResponse>> list(
            @RequestParam(required = false) NewsStatus status,
            @RequestParam(required = false) NewsType type,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(newsService.findStaffAll(status, type, pageable));
    }

    @Operation(summary = "Get any news post by id")
    @GetMapping("/{id}")
    public ResponseEntity<NewsResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(newsService.findStaffById(id));
    }

    @Operation(summary = "Create a news post on behalf of the calling admin")
    @PostMapping
    public ResponseEntity<NewsResponse> create(
            @Valid @RequestBody CreateNewsRequest request,
            Authentication authentication) {
        NewsResponse response = newsService.create(request, StaffPrincipals.staffIdOf(authentication));
        return ResponseEntity.created(URI.create("/v1/news/" + response.getId())).body(response);
    }

    @Operation(summary = "Update any news post (overrides owner restriction)")
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

    @Operation(summary = "Upload (or replace) the image for a news post")
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NewsResponse> uploadImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        String url = mediaProcessingService.storeImage(file, NEWS_IMAGE_SUBDIR, id.toString());
        return ResponseEntity.ok(newsService.setImageUrl(
                id,
                url,
                StaffPrincipals.staffIdOf(authentication),
                StaffPrincipals.roleOf(authentication)));
    }

    @Operation(summary = "Remove the image for a news post")
    @DeleteMapping("/{id}/image")
    public ResponseEntity<NewsResponse> deleteImage(
            @PathVariable UUID id,
            Authentication authentication) {
        mediaProcessingService.deleteIfExists(NEWS_IMAGE_SUBDIR, id.toString());
        return ResponseEntity.ok(newsService.setImageUrl(
                id,
                null,
                StaffPrincipals.staffIdOf(authentication),
                StaffPrincipals.roleOf(authentication)));
    }

    @Operation(summary = "Delete a news post", description = "Soft delete by default; pass hard=true to permanently remove the row")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean hard) {
        if (hard) {
            newsService.hardDelete(id);
        } else {
            newsService.deactivate(id);
        }
        return ResponseEntity.noContent().build();
    }
}
