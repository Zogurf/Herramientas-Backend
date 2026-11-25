package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.PackageTrackingResponse;
import com.logistics.proyect.group5.service.PackageTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seguimiento público por código. La ruta queda fuera de
 * {@code /v1/packages/**} porque ese prefijo exige sesión; el cliente solo
 * necesita conocer el código de la guía para consultar el estado.
 */
@RestController
@RequestMapping("/v1/tracking")
@RequiredArgsConstructor
public class PackageTrackingController {

    private final PackageTrackingService packageTrackingService;

    @GetMapping("/{trackingCode}")
    public ResponseEntity<PackageTrackingResponse> track(@PathVariable String trackingCode) {
        return ResponseEntity.ok(packageTrackingService.findByTrackingCode(trackingCode));
    }
}
