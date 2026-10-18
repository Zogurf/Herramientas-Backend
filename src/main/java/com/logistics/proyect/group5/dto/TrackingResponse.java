package com.logistics.proyect.group5.dto;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TrackingResponse {
    private UUID id;
    private UUID orderId;
    private String status;
    private String location;
    private String description;
    private LocalDateTime eventDate;
}