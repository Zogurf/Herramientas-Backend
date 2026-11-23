package com.logistics.proyect.group5.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta pública de seguimiento de un paquete.
 *
 * <p>Los nombres de los campos coinciden con el contrato que consume la página
 * {@code /tracking/{code}} del frontend. Las fechas se envían en ISO-8601 para
 * que el cliente las formatee en la zona horaria del usuario.</p>
 */
public record PackageTrackingResponse(
        String trackingCode,
        String currentStatus,
        String originCity,
        String destinationCity,
        String senderName,
        String senderPhone,
        String receiverName,
        String receiverPhone,
        String destinationAddress,
        LocalDate estimatedDeliveryDate,
        String courierName,
        String courierPhone,
        String courierAvatar,
        BigDecimal weightKg,
        OffsetDateTime lastUpdate,
        List<Event> history) {

    public record Event(
            UUID id,
            String status,
            String location,
            String description,
            OffsetDateTime timestamp) {
    }
}
