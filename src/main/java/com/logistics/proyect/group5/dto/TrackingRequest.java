package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class TrackingRequest {
    @NotNull(message = "El ID del pedido es obligatorio")
    private UUID orderId;

    @NotBlank(message = "El estado es obligatorio")
    private String status;

    private String location;
    private String description;
}