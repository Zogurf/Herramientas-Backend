package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de estado de un envío. El identificador viaja en la ruta, no en el
 * cuerpo, para que la URL sea la única fuente del paquete afectado.
 */
public record UpdatePackageStatusRequest(
    @NotBlank @Size(max = 50) String newStatus,
    @Size(max = 150) String location,
    @Size(max = 2000) String notes
) {
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("La solicitud contiene campos no admitidos.");
    }
}
