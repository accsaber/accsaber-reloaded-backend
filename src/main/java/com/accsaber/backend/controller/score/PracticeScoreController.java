package com.accsaber.backend.controller.score;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.request.score.PracticeScoreRequest;
import com.accsaber.backend.model.dto.response.PracticeScoreResponse;
import com.accsaber.backend.service.score.PracticeScoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/practice-scores")
@RequiredArgsConstructor
@Tag(name = "Practice Scores")
public class PracticeScoreController {

    private final PracticeScoreService practiceScoreService;

    @Operation(summary = "Submit practice range minigame scores")
    @PostMapping
    public ResponseEntity<Void> submit(@RequestBody List<PracticeScoreRequest> requests) {
        practiceScoreService.submit(requests);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List top practice range minigame scores")
    @GetMapping
    public ResponseEntity<List<PracticeScoreResponse>> top(@RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(practiceScoreService.top(size));
    }
}
