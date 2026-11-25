package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.PackageTrackingResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PackageTrackingServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PackageTrackingService service = new PackageTrackingService(jdbc);

    @Test
    void synthesizesRegistrationEventWhenPackageHasNoEvents() {
        PackageTrackingService.PackageRow pkg = packageRow(UUID.randomUUID(), "NX-ABC");
        when(jdbc.query(contains("FROM package_schema.packages"), anyRowMapper(), eq("NX-ABC")))
                .thenReturn(List.of(pkg));
        when(jdbc.query(contains("FROM package_schema.tracking_events"),
                anyRowMapper(), eq(pkg.id())))
                .thenReturn(List.of());

        PackageTrackingResponse response = service.findByTrackingCode(" nx-abc ");

        assertEquals("NX-ABC", response.trackingCode());
        assertEquals("REGISTRADO", response.currentStatus());
        assertEquals("********0000", response.senderPhone());
        assertEquals("********5678", response.receiverPhone());
        assertEquals(1, response.history().size());
        assertEquals("REGISTRADO", response.history().getFirst().status());
        assertEquals("Lima", response.history().getFirst().location());
        assertEquals(pkg.createdAt(), response.lastUpdate());
    }

    @Test
    void returnsHistoryNewestFirstAndUsesLatestEventAsLastUpdate() {
        PackageTrackingService.PackageRow pkg = packageRow(UUID.randomUUID(), "NX-ABC");
        OffsetDateTime older = OffsetDateTime.parse("2026-11-20T10:00:00Z");
        OffsetDateTime newer = OffsetDateTime.parse("2026-11-21T15:30:00Z");
        when(jdbc.query(contains("FROM package_schema.packages"), anyRowMapper(), eq("NX-ABC")))
                .thenReturn(List.of(pkg));
        when(jdbc.query(contains("FROM package_schema.tracking_events"),
                anyRowMapper(), eq(pkg.id())))
                .thenReturn(List.of(
                        event(UUID.randomUUID(), "REGISTRADO", older),
                        event(UUID.randomUUID(), "EN_TRANSITO", newer)));

        PackageTrackingResponse response = service.findByTrackingCode("NX-ABC");

        assertEquals(2, response.history().size());
        assertEquals("EN_TRANSITO", response.history().getFirst().status());
        assertEquals("REGISTRADO", response.history().getLast().status());
        assertEquals(newer, response.lastUpdate());
    }

    @Test
    void missingCodeThrowsNotFoundWithoutDatabaseDetails() {
        when(jdbc.query(contains("FROM package_schema.packages"),
                anyRowMapper(), eq("NX-MISSING")))
                .thenReturn(List.of());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.findByTrackingCode("NX-MISSING"));

        assertEquals("Envío no encontrado.", error.getMessage());
    }

    @Test
    void blankCodeIsRejectedBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> service.findByTrackingCode("   "));
        verifyNoInteractions(jdbc);
    }

    @SuppressWarnings("unchecked")
    private static <T> RowMapper<T> anyRowMapper() {
        return any(RowMapper.class);
    }

    private static PackageTrackingService.PackageRow packageRow(UUID id, String trackingCode) {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-11-20T10:00:00Z");
        return new PackageTrackingService.PackageRow(
                id, trackingCode, "María Rojas", "+51900000000", "Destinatario",
                "+51912345678", "Lima", "Arequipa", "Av. Central 123",
                new BigDecimal("12.50"), "REGISTRADO", LocalDate.parse("2026-11-25"),
                createdAt, createdAt, null, null, null);
    }

    private static PackageTrackingResponse.Event event(
            UUID id, String status, OffsetDateTime timestamp) {
        return new PackageTrackingResponse.Event(id, status, "Lima", "Descripción", timestamp);
    }
}
