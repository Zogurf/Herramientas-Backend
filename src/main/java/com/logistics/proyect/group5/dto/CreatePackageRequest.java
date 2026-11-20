package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreatePackageRequest(
    @NotBlank @Size(max = 150) String receiverName,
    @NotBlank @Size(max = 20)
    @Pattern(regexp = "^\\+?[0-9][0-9\\s-]{5,19}$", message = "El teléfono del destinatario no es válido")
    String receiverPhone,
    @NotBlank @Size(max = 1000) String destinationAddress,
    @NotBlank @Size(max = 100) String destinationCity,
    @NotNull @DecimalMin("0.01") @DecimalMax("9999.99")
    @Digits(integer = 4, fraction = 2) BigDecimal weightKg
) {
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("La solicitud contiene campos no admitidos.");
    }
}
