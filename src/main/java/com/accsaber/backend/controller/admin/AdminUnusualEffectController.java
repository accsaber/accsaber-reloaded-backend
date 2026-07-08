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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.item.CreateUnusualEffectRequest;
import com.accsaber.backend.model.dto.request.item.UpdateUnusualEffectRequest;
import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.item.UnusualEffectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/unusual-effects")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Unusual Effects")
public class AdminUnusualEffectController {

    private final UnusualEffectService unusualEffectService;

    @Operation(summary = "List unusual effects (admin)")
    @GetMapping
    public ResponseEntity<List<UnusualEffectResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(unusualEffectService.findAll(includeInactive).stream()
                .map(ItemMapper::toUnusualEffectResponse)
                .toList());
    }

    @Operation(summary = "Get an unusual effect by id")
    @GetMapping("/{id}")
    public ResponseEntity<UnusualEffectResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toUnusualEffectResponse(unusualEffectService.findById(id)));
    }

    @Operation(summary = "Create an unusual effect")
    @PostMapping
    public ResponseEntity<UnusualEffectResponse> create(@Valid @RequestBody CreateUnusualEffectRequest req) {
        var effect = unusualEffectService.create(req.getKey(), req.getName(),
                req.getDescription(), req.getEffectSpec());
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemMapper.toUnusualEffectResponse(effect));
    }

    @Operation(summary = "Update an unusual effect")
    @PatchMapping("/{id}")
    public ResponseEntity<UnusualEffectResponse> update(@PathVariable UUID id,
            @RequestBody UpdateUnusualEffectRequest req) {
        var effect = unusualEffectService.update(id, req.getName(), req.getDescription(), req.getEffectSpec());
        return ResponseEntity.ok(ItemMapper.toUnusualEffectResponse(effect));
    }

    @Operation(summary = "Deactivate an unusual effect")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        unusualEffectService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate a previously deactivated unusual effect")
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<UnusualEffectResponse> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ItemMapper.toUnusualEffectResponse(unusualEffectService.reactivate(id)));
    }
}
