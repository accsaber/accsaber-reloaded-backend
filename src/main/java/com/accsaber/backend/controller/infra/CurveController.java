package com.accsaber.backend.controller.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.CurveResponse;
import com.accsaber.backend.service.infra.CurveService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/curves")
@RequiredArgsConstructor
@Tag(name = "Platform")
public class CurveController {

    private final CurveService curveService;

    @Operation(summary = "List curves")
    @GetMapping
    public ResponseEntity<List<CurveResponse>> getAllCurves() {
        return ResponseEntity.ok(curveService.findAllActive());
    }

    @Operation(summary = "Get a curve", description = "Point curves have around a thousand points. Read it once "
            + "and keep it.")
    @GetMapping("/{id}")
    public ResponseEntity<CurveResponse> getCurve(@PathVariable UUID id) {
        return ResponseEntity.ok(curveService.findById(id));
    }
}
