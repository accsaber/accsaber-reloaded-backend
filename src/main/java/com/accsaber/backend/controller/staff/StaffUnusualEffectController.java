package com.accsaber.backend.controller.staff;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.item.UnusualEffectResponse;
import com.accsaber.backend.service.item.ItemMapper;
import com.accsaber.backend.service.item.UnusualEffectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/staff/unusual-effects")
@PreAuthorize("hasAnyRole('ADMIN', 'CREATIVE')")
@RequiredArgsConstructor
@Tag(name = "Staff Items")
public class StaffUnusualEffectController {

    private final UnusualEffectService unusualEffectService;

    @Operation(summary = "List unusual effects including unreleased inactive ones, for staff browsing and previewing")
    @GetMapping
    public ResponseEntity<List<UnusualEffectResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(unusualEffectService.findAll(includeInactive).stream()
                .map(ItemMapper::toUnusualEffectResponse)
                .toList());
    }
}
