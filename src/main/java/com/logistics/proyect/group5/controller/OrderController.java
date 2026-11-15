package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.CreateOrderRequest;
import com.logistics.proyect.group5.dto.OrderResponse;
import com.logistics.proyect.group5.model.User;
import com.logistics.proyect.group5.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.UUID;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal User user
    ) {
        return orderService.createOrder(request, user);
    }

    @PostMapping("/{id}/confirm-payment")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse confirmPayment(@PathVariable UUID id) {
        return orderService.confirmPayment(id);
    }
}
