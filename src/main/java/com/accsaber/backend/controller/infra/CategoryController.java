package com.accsaber.backend.controller.infra;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.CategoryResponse;
import com.accsaber.backend.model.dto.response.map.ReweightRoundResponse;
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

    @Operation(summary = "List the scoring categories", description = "The categories a map can be ranked in, so True Acc, "
            + "Standard Acc, Tech Acc and the rest, each with the curves it uses to work out AP. Overall is in here too, and it "
            + "aggregates across whichever categories are set to count toward it.")
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories() {
        return ResponseEntity.ok(categoryService.findAllActive());
    }

    @Operation(summary = "Get one category", description = "The same thing as the list but for a single category. You can address "
            + "it either by its UUID or by its code, so /v1/categories/true_acc works just as well as passing the id.")
    @GetMapping("/{category}")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable String category) {
        return ResponseEntity.ok(categoryService.findById(categoryService.resolveId(category)));
    }

    @Operation(summary = "List a category's reweight rounds", description = "Every time the ranking team changes the "
            + "complexity of ranked maps, that lands as one round per category, oldest first. It is meant for drawing markers "
            + "on history charts. Overall gives you the rounds of every live category. The maps changed in a round come "
            + "along with it when there are five or fewer of them, and maps is null on bigger rounds.")
    @GetMapping("/{category}/reweights")
    public ResponseEntity<List<ReweightRoundResponse>> listReweights(@PathVariable String category) {
        return ResponseEntity.ok(reweightRoundService.findForCategory(categoryService.resolveId(category)));
    }
}
