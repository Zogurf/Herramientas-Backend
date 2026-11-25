package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.PackageTrackingResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Consulta pública de paquetes por código de seguimiento.
 *
 * <p>La tabla {@code package_schema.tracking_events} puede estar vacía para
 * paquetes recién registrados. En ese caso se sintetiza el evento
 * {@code REGISTRADO} con la fecha de creación, de modo que la línea de tiempo
 * nunca aparece vacía. El historial se devuelve del evento más reciente al más
 * antiguo, que es el orden que espera el componente de timeline del frontend.</p>
 */
@Service
@RequiredArgsConstructor
public class PackageTrackingService {

    private static final String REGISTERED_STATUS = "REGISTRADO";

    private static final String PACKAGE_SELECT = """
            SELECT p.id, p.tracking_code, sender.full_name AS sender_name,
                   sender.phone AS sender_phone, p.recipient_name AS receiver_name,
                   p.recipient_phone AS receiver_phone, p.origin_city,
                   p.destination_city, p.destination_address, p.weight_kg,
                   p.current_status::text AS status, p.estimated_delivery,
                   p.created_at, p.updated_at,
                   courier.full_name AS courier_name, courier.phone AS courier_phone,
                   courier.avatar_url AS courier_avatar
            FROM package_schema.packages p
            LEFT JOIN auth_schema.users sender ON sender.id = p.sender_id
            LEFT JOIN auth_schema.users courier ON courier.id = p.courier_id
            """;

    private static final String EVENTS_SELECT = """
            SELECT id, status::text AS status, location, description, registered_at
            FROM package_schema.tracking_events
            WHERE package_id = ?
            ORDER BY registered_at ASC, id ASC
            """;

    private static final RowMapper<PackageRow> PACKAGE_MAPPER = PackageTrackingService::mapPackage;
    private static final RowMapper<PackageTrackingResponse.Event> EVENT_MAPPER =
            PackageTrackingService::mapEvent;

    private final JdbcTemplate jdbc;

    public PackageTrackingResponse findByTrackingCode(String trackingCode) {
        if (trackingCode == null || trackingCode.isBlank()) {
            throw new IllegalArgumentException("El código de seguimiento es obligatorio.");
        }

        PackageRow pkg = jdbc.query(
                        PACKAGE_SELECT + " WHERE UPPER(p.tracking_code) = ?",
                        PACKAGE_MAPPER,
                        trackingCode.trim().toUpperCase(Locale.ROOT))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Envío no encontrado."));

        List<PackageTrackingResponse.Event> history = new ArrayList<>(
                jdbc.query(EVENTS_SELECT, EVENT_MAPPER, pkg.id()));

        boolean hasRegistration = history.stream()
                .anyMatch(event -> REGISTERED_STATUS.equals(event.status()));
        if (!hasRegistration) {
            history.add(new PackageTrackingResponse.Event(
                    UUID.nameUUIDFromBytes(("package:" + pkg.id()).getBytes(StandardCharsets.UTF_8)),
                    REGISTERED_STATUS,
                    pkg.originCity(),
                    "Envío registrado en el sistema.",
                    pkg.createdAt()));
        }

        history.sort(Comparator.comparing(
                PackageTrackingResponse.Event::timestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        Collections.reverse(history);

        OffsetDateTime lastUpdate = history.isEmpty()
                ? pkg.updatedAt()
                : history.getFirst().timestamp();

        return new PackageTrackingResponse(
                pkg.trackingCode(),
                pkg.status(),
                pkg.originCity(),
                pkg.destinationCity(),
                pkg.senderName(),
                maskPhone(pkg.senderPhone()),
                pkg.receiverName(),
                maskPhone(pkg.receiverPhone()),
                pkg.destinationAddress(),
                pkg.estimatedDelivery(),
                pkg.courierName(),
                pkg.courierPhone(),
                pkg.courierAvatar(),
                pkg.weightKg(),
                lastUpdate,
                List.copyOf(history));
    }

    private static PackageRow mapPackage(ResultSet rs, int rowNum) throws SQLException {
        return new PackageRow(
                rs.getObject("id", UUID.class),
                rs.getString("tracking_code"),
                rs.getString("sender_name"),
                rs.getString("sender_phone"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("origin_city"),
                rs.getString("destination_city"),
                rs.getString("destination_address"),
                rs.getBigDecimal("weight_kg"),
                rs.getString("status"),
                rs.getObject("estimated_delivery", LocalDate.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getString("courier_name"),
                rs.getString("courier_phone"),
                rs.getString("courier_avatar"));
    }

    private static PackageTrackingResponse.Event mapEvent(ResultSet rs, int rowNum)
            throws SQLException {
        return new PackageTrackingResponse.Event(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getString("location"),
                rs.getString("description"),
                rs.getObject("registered_at", OffsetDateTime.class));
    }

    /**
     * El seguimiento es público: el código de guía actúa como credencial, pero
     * no es necesario devolver los teléfonos completos. Se conservan los
     * últimos cuatro dígitos para que el cliente pueda identificar el contacto.
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    /** Fila de paquete; visible en el paquete para poder probar el mapeo. */
    record PackageRow(UUID id, String trackingCode, String senderName, String senderPhone,
                      String receiverName, String receiverPhone, String originCity,
                      String destinationCity, String destinationAddress, BigDecimal weightKg,
                      String status, LocalDate estimatedDelivery, OffsetDateTime createdAt,
                      OffsetDateTime updatedAt, String courierName, String courierPhone,
                      String courierAvatar) {
    }
}
