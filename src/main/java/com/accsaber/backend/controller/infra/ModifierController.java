package com.accsaber.backend.controller.infra;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.ModifierResponse;
import com.accsaber.backend.service.infra.ModifierService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/modifiers")
@RequiredArgsConstructor
@Tag(name = "Modifiers")
public class ModifierController {

    private final ModifierService modifierService;

    @Operation(summary = "List active modifiers", description = "Returns all active score modifiers")
    @GetMapping
    public ResponseEntity<List<ModifierResponse>> listModifiers() {
        return ResponseEntity.ok(modifierService.findAllActive());
    }
}
