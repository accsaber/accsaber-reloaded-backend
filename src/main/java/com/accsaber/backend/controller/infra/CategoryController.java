package com.accsaber.backend.controller.infra;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.CategoryResponse;
import com.accsaber.backend.model.dto.response.map.ReweightDayResponse;
import com.accsaber.backend.service.infra.CategoryService;
import com.accsaber.backend.service.map.ReweightRoundService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Platform")
public class CategoryController {

    private final CategoryService categoryService;
    private final ReweightRoundService reweightRoundService;

    @Operation(summary = "List categories", description = "Overall is included and adds up whichever "
            + "categories count toward it.")
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.findAllActive());
    }

    @Operation(summary = "Get a category", description = "Takes the UUID or the code. /v1/categories/true_acc "
            + "works.")
    @GetMapping("/{category}")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable String category) {
        return ResponseEntity.ok(categoryService.findById(categoryService.resolveId(category)));
    }

    @Operation(summary = "Reweight days", description = "One entry per UTC day, oldest first. For "
            + "overall, categoryCodes says which ones moved. maps is null on days with more than five changed "
            + "maps.")
    @GetMapping("/{category}/reweights")
    public ResponseEntity<List<ReweightDayResponse>> listReweights(@PathVariable String category) {
        return ResponseEntity.ok(reweightRoundService.findForCategory(categoryService.resolveId(category)));
    }
}
