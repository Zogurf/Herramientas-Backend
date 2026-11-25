package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class TrackingRequest {
    @NotNull(message = "El ID del pedido es obligatorio")
    private UUID orderId;

    @NotBlank(message = "El estado es obligatorio")
    @Size(max = 50, message = "El estado no puede superar 50 caracteres")
    @Pattern(
            regexp = "REGISTRADO|EN_ALMACEN|EN_TRANSITO|EN_RUTA|ENTREGADO|INCIDENCIA|PENDIENTE|CANCELADO",
            message = "El estado no es válido. Usa REGISTRADO, EN_ALMACEN, EN_TRANSITO, EN_RUTA, ENTREGADO, INCIDENCIA, PENDIENTE o CANCELADO"
    )
    private String status;

    @Size(max = 200, message = "La ubicación no puede superar 200 caracteres")
    private String location;

    @Size(max = 5000, message = "La descripción no puede superar 5000 caracteres")
    private String description;
}
