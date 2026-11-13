package com.logistics.proyect.group5.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PackageResponse(UUID id, String trackingCode, String senderName,
    String senderPhone, String receiverName, String receiverPhone,
    String originCity, String destinationCity, String destinationAddress,
    BigDecimal weightKg, String status, String description,
    OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
