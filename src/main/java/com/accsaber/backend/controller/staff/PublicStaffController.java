package com.accsaber.backend.controller.staff;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.model.dto.response.staff.PublicStaffUserResponse;
import com.accsaber.backend.service.staff.StaffUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/staff/users-public")
@RequiredArgsConstructor
@Tag(name = "Staff Accounts")
public class PublicStaffController {

    private final StaffUserService staffUserService;

    @Operation(summary = "List staff")
    @GetMapping
    public ResponseEntity<Page<PublicStaffUserResponse>> listStaffUsers(
            @PageableDefault(size = 20, sort = "username") Pageable pageable,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(staffUserService.getAllPublic(pageable, active));
    }

    @Operation(summary = "Get a staff user")
    @GetMapping("/{id}")
    public ResponseEntity<PublicStaffUserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(staffUserService.getByIdPublic(id));
    }
}
