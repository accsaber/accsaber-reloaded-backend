package com.accsaber.backend.controller.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.admin.RunJobRequest;
import com.accsaber.backend.model.dto.response.admin.JobResponse;
import com.accsaber.backend.model.dto.response.admin.JobTypeResponse;
import com.accsaber.backend.service.admin.AdminJobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Operations")
public class AdminJobController {

    private final AdminJobService jobService;

    @Operation(summary = "List job types", description = "Every job you can start and the fields each one "
            + "takes. New job types show up here on their own.")
    @GetMapping("/types")
    public ResponseEntity<List<JobTypeResponse>> types() {
        return ResponseEntity.ok(jobService.catalogue());
    }

    @Operation(summary = "Start a job", description = "Gives you the job id right away. A missing field "
            + "gets a 422. Starting the same job twice runs it twice.")
    @PostMapping
    public ResponseEntity<JobResponse> run(@Valid @RequestBody RunJobRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.from(jobService.run(request)));
    }

    @Operation(summary = "Running and recent jobs", description = "Running jobs first, then the last 50 finished. A restart clears "
            + "the list.")
    @GetMapping
    public ResponseEntity<List<JobResponse>> list() {
        return ResponseEntity.ok(jobService.list().stream().map(JobResponse::from).toList());
    }

    @Operation(summary = "Check a job", description = "Shows the error if it failed. Old jobs fall off the list and "
            + "start giving 404.")
    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> get(@PathVariable UUID jobId) {
        return ResponseEntity.ok(JobResponse.from(jobService.find(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId))));
    }
}
