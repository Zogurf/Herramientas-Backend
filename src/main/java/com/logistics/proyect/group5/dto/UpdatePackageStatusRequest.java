package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePackageStatusRequest(
    @NotBlank @Size(max = 30) String newStatus,
    @NotBlank @Size(max = 150) String location,
    @Size(max = 5000) String notes
) {
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("La solicitud contiene campos no admitidos.");
    }
}
