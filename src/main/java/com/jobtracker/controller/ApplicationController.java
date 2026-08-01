package com.jobtracker.controller;

import com.jobtracker.dto.ApplicationRequest;
import com.jobtracker.dto.ApplicationResponse;
import com.jobtracker.model.ApplicationStatus;
import com.jobtracker.service.ApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> list(
            Authentication auth,
            @RequestParam(required = false) ApplicationStatus status
    ) {
        return ResponseEntity.ok(applicationService.listForUser(auth.getName(), status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getOne(Authentication auth, @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getOne(auth.getName(), id));
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            Authentication auth,
            @Valid @RequestBody ApplicationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(
            Authentication auth,
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationRequest request
    ) {
        return ResponseEntity.ok(applicationService.update(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        applicationService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
