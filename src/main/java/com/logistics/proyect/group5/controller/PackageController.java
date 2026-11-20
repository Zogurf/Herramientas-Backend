package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.CreatePackageRequest;
import com.logistics.proyect.group5.dto.PackageResponse;
import com.logistics.proyect.group5.model.User;
import com.logistics.proyect.group5.service.PackageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/packages")
@RequiredArgsConstructor
public class PackageController {
    private final PackageService packageService;

    @PostMapping
    public ResponseEntity<PackageResponse> create(
            @Valid @RequestBody CreatePackageRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(packageService.create(request, user.getId()));
    }

    @GetMapping
    public ResponseEntity<List<PackageResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String status) {
        return ResponseEntity.ok(packageService.findAll(search, status));
    }

    @GetMapping("/track/{trackingCode}")
    public ResponseEntity<PackageResponse> findByTrackingCode(@PathVariable String trackingCode) {
        return ResponseEntity.ok(packageService.findByTrackingCode(trackingCode));
    }
}
