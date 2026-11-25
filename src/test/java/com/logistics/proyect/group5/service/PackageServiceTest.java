package com.logistics.proyect.group5.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.proyect.group5.dto.CreatePackageRequest;
import com.logistics.proyect.group5.dto.PackageResponse;
import com.logistics.proyect.group5.dto.UpdatePackageStatusRequest;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PackageServiceTest {
    @Mock
    private JdbcTemplate jdbc;

    private PackageService packageService;

    @BeforeEach
    void setUp() {
        packageService = new PackageService(jdbc, "Lima");
    }

    @Test
    void createUsesExistingColumnsAndMapsDatabaseResponse() throws Exception {
        UUID id = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T20:00:00Z");
        CreatePackageRequest request = new CreatePackageRequest(
                "  María Rojas ", "+51912345678", "  Av. Central 123 ",
                "  Arequipa ", new BigDecimal("12.50"));

        when(jdbc.queryForObject(anyString(), anyRowMapper(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    Object[] parameters = Arrays.copyOfRange(invocation.getArguments(), 2,
                            invocation.getArguments().length);
                    String insertColumns = sql.substring(sql.indexOf("INSERT INTO"), sql.indexOf("RETURNING"));
                    assertTrue(insertColumns.contains("tracking_code, sender_id, recipient_name"));
                    assertFalse(sql.contains("tracking_code_seq"));
                    assertFalse(insertColumns.contains("sender_name"));
                    assertFalse(insertColumns.contains("sender_phone"));
                    assertFalse(insertColumns.contains("description"));
                    assertEquals(senderId, parameters[1]);
                    assertEquals("María Rojas", parameters[2]);
                    assertEquals("+51912345678", parameters[3]);
                    assertEquals("Av. Central 123", parameters[4]);
                    assertEquals("Lima", parameters[5]);
                    assertEquals("Arequipa", parameters[6]);
                    assertEquals(new BigDecimal("12.50"), parameters[7]);

                    String trackingCode = (String) parameters[0];
                    assertEquals(30, trackingCode.length());
                    assertTrue(trackingCode.matches("NX-[A-F0-9]{27}"));

                    ResultSet rs = packageResultSet(id, trackingCode, now);
                    return rowMapper(invocation).mapRow(rs, 1);
                });

        PackageResponse response = packageService.create(request, senderId);

        assertEquals(id, response.id());
        assertEquals("María Rojas", response.senderName());
        assertEquals("+51900000000", response.senderPhone());
        assertEquals("Destinatario", response.receiverName());
        assertEquals("Lima", response.originCity());
        assertEquals("REGISTRADO", response.status());
        assertEquals(now, response.createdAt());
        verify(jdbc).queryForObject(anyString(), anyRowMapper(), any(Object[].class));
    }

    @Test
    void listFiltersByTrackingCodeSenderRecipientAndStatus() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T20:00:00Z");
        when(jdbc.query(anyString(), anyRowMapper(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertTrue(sql.contains("p.tracking_code ILIKE ?"));
                    assertTrue(sql.contains("sender.full_name ILIKE ?"));
                    assertTrue(sql.contains("p.recipient_name ILIKE ?"));
                    assertTrue(sql.contains("p.current_status::text = ?"));
                    Object[] parameters = Arrays.copyOfRange(invocation.getArguments(), 2,
                            invocation.getArguments().length);
                    assertArrayEquals(new Object[]{"%María%", "%María%", "%María%", "EN_TRANSITO"}, parameters);
                    return List.of(rowMapper(invocation).mapRow(
                            packageResultSet(UUID.randomUUID(), "NX-ABC", now), 1));
                });

        List<PackageResponse> packages = packageService.findAll(" María ", "en_transito");

        assertEquals(1, packages.size());
        assertEquals("NX-ABC", packages.get(0).trackingCode());
        assertEquals("María Rojas", packages.get(0).senderName());
    }

    @Test
    void lookupByTrackingCodeIsCaseInsensitiveAndMapsResponse() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T20:00:00Z");
        when(jdbc.query(anyString(), anyRowMapper(), eq("NX-ABC")))
                .thenAnswer(invocation -> List.of(rowMapper(invocation).mapRow(
                        packageResultSet(UUID.randomUUID(), "NX-ABC", now), 1)));

        PackageResponse response = packageService.findByTrackingCode(" nx-abc ");

        assertEquals("NX-ABC", response.trackingCode());
        verify(jdbc).query(contains("UPPER(p.tracking_code) = ?"), anyRowMapper(), eq("NX-ABC"));
    }

    @Test
    void missingTrackingCodeReturnsNotFoundWithoutDatabaseDetails() {
        when(jdbc.query(anyString(), anyRowMapper(), eq("NX-MISSING"))).thenReturn(List.of());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> packageService.findByTrackingCode("nx-missing"));

        assertEquals("Envío no encontrado.", error.getMessage());
    }

    @Test
    void findByIdMapsPackageAndMissingIdIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T20:00:00Z");
        when(jdbc.query(contains("WHERE p.id = ?"), anyRowMapper(), eq(id)))
                .thenAnswer(invocation -> List.of(rowMapper(invocation).mapRow(
                        packageResultSet(id, "NX-ABC", now), 1)));

        PackageResponse response = packageService.findById(id);

        assertEquals(id, response.id());
        assertEquals("NX-ABC", response.trackingCode());

        UUID missing = UUID.randomUUID();
        when(jdbc.query(contains("WHERE p.id = ?"), anyRowMapper(), eq(missing)))
                .thenReturn(List.of());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class, () -> packageService.findById(missing));
        assertEquals("Envío no encontrado.", error.getMessage());
    }

    @Test
    void updateStatusWritesPackageAndTrackingEvent() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-11-19T20:00:00Z");
        when(jdbc.query(contains("WHERE p.id = ?"), anyRowMapper(), eq(id)))
                .thenAnswer(invocation -> List.of(rowMapper(invocation).mapRow(
                        packageResultSet(id, "NX-ABC", now, "EN_TRANSITO"), 1)));

        PackageResponse response = packageService.updateStatus(id,
                new UpdatePackageStatusRequest(" en_transito ", " Hub Lima ", " Salida del almacén "));

        assertEquals("EN_TRANSITO", response.status());
        verify(jdbc).update(contains("UPDATE package_schema.packages"),
                eq("EN_TRANSITO"), eq(id));
        verify(jdbc).update(contains("INSERT INTO package_schema.tracking_events"),
                eq(id), eq("EN_TRANSITO"), eq("Hub Lima"), eq("Salida del almacén"));
    }

    @Test
    void updateStatusRejectsUnknownStatusWithoutTouchingDatabase() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> packageService.updateStatus(UUID.randomUUID(),
                        new UpdatePackageStatusRequest("PERDIDO", null, null)));

        assertEquals("Estado de envío no válido.", error.getMessage());
        verifyNoInteractions(jdbc);
    }

    @Test
    void generatedTrackingCodesFitDatabaseColumn() {
        String trackingCode = PackageService.generateTrackingCode();

        assertTrue(trackingCode.matches("NX-[A-F0-9]{27}"));
        assertTrue(trackingCode.length() <= 30);
    }

    @Test
    void requestRejectsFieldsThatCannotBePersisted() {
        ObjectMapper mapper = new ObjectMapper();

        assertThrows(Exception.class, () -> mapper.readValue("""
                {"receiverName":"Persona","receiverPhone":"+51912345678",
                 "destinationAddress":"Dirección","destinationCity":"Lima",
                 "weightKg":1.25,"senderName":"No persistible"}
                """, CreatePackageRequest.class));
    }

    @Test
    void internalDatabaseErrorsAreNotReturnedToClients() {
        var response = new com.logistics.proyect.group5.exception.GlobalExceptionHandler()
                .handleGenericException(new RuntimeException("internal database details"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("No se pudo completar la solicitud", response.getBody().get("message"));
        assertFalse(response.getBody().toString().contains("internal database details"));
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<PackageResponse> anyRowMapper() {
        return any(RowMapper.class);
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<PackageResponse> rowMapper(
            org.mockito.invocation.InvocationOnMock invocation) {
        return invocation.getArgument(1);
    }

    private static ResultSet packageResultSet(UUID id, String trackingCode, OffsetDateTime now)
            throws Exception {
        return packageResultSet(id, trackingCode, now, "REGISTRADO");
    }

    private static ResultSet packageResultSet(UUID id, String trackingCode, OffsetDateTime now,
            String status) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        when(rs.getString("tracking_code")).thenReturn(trackingCode);
        when(rs.getString("sender_name")).thenReturn("María Rojas");
        when(rs.getString("sender_phone")).thenReturn("+51900000000");
        when(rs.getString("receiver_name")).thenReturn("Destinatario");
        when(rs.getString("receiver_phone")).thenReturn("+51912345678");
        when(rs.getString("origin_city")).thenReturn("Lima");
        when(rs.getString("destination_city")).thenReturn("Arequipa");
        when(rs.getString("destination_address")).thenReturn("Av. Central 123");
        when(rs.getBigDecimal("weight_kg")).thenReturn(new BigDecimal("12.50"));
        when(rs.getString("status")).thenReturn(status);
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(now);
        when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(now);
        return rs;
    }
}
