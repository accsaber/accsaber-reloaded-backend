package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.model.dto.request.mission.EventRequest;
import com.accsaber.backend.model.dto.response.mission.EventResponse;
import com.accsaber.backend.model.dto.response.mission.MissionTemplateResponse;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;
import com.accsaber.backend.service.mission.EventMissionService;
import com.accsaber.backend.service.mission.EventService;
import com.accsaber.backend.service.mission.MissionTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/events")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Events")
public class AdminEventController {

    private static final String EVENT_BACKGROUND_SUBDIR = "events";
    private static final String EVENT_ICON_SUBDIR = "event-icons";

    private final EventService eventService;
    private final EventMissionService eventMissionService;
    private final MissionTemplateService templateService;
    private final MediaProcessingService mediaProcessingService;

    @Operation(summary = "List all events")
    @GetMapping
    public ResponseEntity<List<EventResponse>> list() {
        return ResponseEntity.ok(eventService.listAdmin().stream()
                .map(EventResponse::from).toList());
    }

    @Operation(summary = "Get an event")
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(EventResponse.from(eventService.findById(id)));
    }

    @Operation(summary = "List an event's mission templates")
    @GetMapping("/{id}/missions")
    public ResponseEntity<List<MissionTemplateResponse>> listMissions(@PathVariable UUID id) {
        eventService.findById(id);
        return ResponseEntity.ok(templateService.listByEvent(id).stream()
                .map(MissionTemplateResponse::from).toList());
    }

    @Operation(summary = "Create an event")
    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EventResponse.from(eventService.create(req)));
    }

    @Operation(summary = "Update an event")
    @PatchMapping("/{id}")
    public ResponseEntity<EventResponse> update(@PathVariable UUID id,
            @Valid @RequestBody EventRequest req) {
        return ResponseEntity.ok(EventResponse.from(eventService.update(id, req)));
    }

    @Operation(summary = "Deactivate an event")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Roll out event missions to all eligible users",
            description = "Async. Creates missing user missions for every currently unlocked mission of the event.")
    @PostMapping("/{id}/rollout")
    public ResponseEntity<Void> rollout(@PathVariable UUID id) {
        eventMissionService.rolloutEvent(id);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Upload (or replace) the background image for an event")
    @PostMapping(value = "/{id}/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> uploadBackground(@PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        String url = mediaProcessingService.storeImage(file, EVENT_BACKGROUND_SUBDIR, id.toString());
        return ResponseEntity.ok(EventResponse.from(eventService.setBackgroundUrl(id, url)));
    }

    @Operation(summary = "Remove the background image for an event")
    @DeleteMapping("/{id}/background")
    public ResponseEntity<EventResponse> deleteBackground(@PathVariable UUID id) {
        mediaProcessingService.deleteIfExists(EVENT_BACKGROUND_SUBDIR, id.toString());
        return ResponseEntity.ok(EventResponse.from(eventService.setBackgroundUrl(id, null)));
    }

    @Operation(summary = "Upload (or replace) the icon image for an event")
    @PostMapping(value = "/{id}/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EventResponse> uploadIcon(@PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        String url = mediaProcessingService.storeImage(file, EVENT_ICON_SUBDIR, id.toString(), MediaFormat.PNG);
        return ResponseEntity.ok(EventResponse.from(eventService.setIconUrl(id, url)));
    }

    @Operation(summary = "Remove the icon image for an event")
    @DeleteMapping("/{id}/icon")
    public ResponseEntity<EventResponse> deleteIcon(@PathVariable UUID id) {
        mediaProcessingService.deleteIfExists(EVENT_ICON_SUBDIR, id.toString());
        return ResponseEntity.ok(EventResponse.from(eventService.setIconUrl(id, null)));
    }
}
