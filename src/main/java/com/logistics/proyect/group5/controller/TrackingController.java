package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.TrackingRequest;
import com.logistics.proyect.group5.dto.TrackingResponse;
import com.logistics.proyect.group5.service.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;

    /**
     * Endpoint GET: Devuelve la línea de tiempo de un pedido específico.
     * Ideal para alimentar la vista de rastreo del cliente en el frontend.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<List<TrackingResponse>> getTimeline(@PathVariable UUID orderId) {
        return ResponseEntity.ok(trackingService.getTimeline(orderId));
    }

    /**
     * Endpoint POST: Registra una actualización de estado.
     * Protegido solo para el personal interno o sistemas de logística.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrackingResponse> registerEvent(@Valid @RequestBody TrackingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trackingService.registerEvent(request));
    }
}