package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.CreatePackageRequest;
import com.logistics.proyect.group5.dto.PackageResponse;
import com.logistics.proyect.group5.dto.UpdatePackageStatusRequest;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PackageService {
    private static final Set<String> PACKAGE_STATUSES = Set.of(
            "REGISTRADO", "EN_ALMACEN", "EN_TRANSITO", "EN_RUTA", "ENTREGADO", "INCIDENCIA");
    private static final String PACKAGE_SELECT = """
            SELECT p.id, p.tracking_code, sender.full_name AS sender_name,
                   sender.phone AS sender_phone, p.recipient_name AS receiver_name,
                   p.recipient_phone AS receiver_phone, p.origin_city,
                   p.destination_city, p.destination_address, p.weight_kg,
                   p.current_status::text AS status, p.created_at, p.updated_at
            FROM package_schema.packages p
            LEFT JOIN auth_schema.users sender ON sender.id = p.sender_id
            """;
    private static final RowMapper<PackageResponse> PACKAGE_ROW_MAPPER = PackageService::mapPackage;

    private final JdbcTemplate jdbc;
    private final String originCity;

    public PackageService(JdbcTemplate jdbc,
            @Value("${app.packages.origin-city:Lima}") String originCity) {
        this.jdbc = jdbc;
        this.originCity = originCity;
    }

    @Transactional
    public PackageResponse create(CreatePackageRequest request, UUID senderId) {
        String trackingCode = generateTrackingCode();
        return jdbc.queryForObject("""
            WITH created AS (
                INSERT INTO package_schema.packages
                    (tracking_code, sender_id, recipient_name, recipient_phone,
                     destination_address, origin_city, destination_city, weight_kg)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, tracking_code, sender_id, recipient_name, recipient_phone,
                          destination_address, origin_city, destination_city, weight_kg,
                          current_status, created_at, updated_at
            )
            SELECT created.id, created.tracking_code, sender.full_name AS sender_name,
                   sender.phone AS sender_phone, created.recipient_name AS receiver_name,
                   created.recipient_phone AS receiver_phone, created.origin_city,
                   created.destination_city, created.destination_address, created.weight_kg,
                   created.current_status::text AS status, created.created_at, created.updated_at
            FROM created
            LEFT JOIN auth_schema.users sender ON sender.id = created.sender_id
            """, PACKAGE_ROW_MAPPER, trackingCode, senderId, request.receiverName().trim(),
                request.receiverPhone().trim(), request.destinationAddress().trim(),
                originCity.trim(), request.destinationCity().trim(), request.weightKg());
    }

    public List<PackageResponse> findAll(String search, String status) {
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!normalizedStatus.isEmpty() && !"ALL".equals(normalizedStatus)
                && !PACKAGE_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Estado de envío no válido.");
        }

        StringBuilder sql = new StringBuilder(PACKAGE_SELECT).append(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (!normalizedSearch.isEmpty()) {
            sql.append("""
                     AND (p.tracking_code ILIKE ? OR sender.full_name ILIKE ?
                          OR p.recipient_name ILIKE ?)
                    """);
            String pattern = "%" + normalizedSearch + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (!normalizedStatus.isEmpty() && !"ALL".equals(normalizedStatus)) {
            sql.append(" AND p.current_status::text = ?");
            parameters.add(normalizedStatus);
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC");

        return jdbc.query(sql.toString(), PACKAGE_ROW_MAPPER, parameters.toArray());
    }

    @Transactional
    public PackageResponse updateStatus(UUID packageId, UpdatePackageStatusRequest request) {
        String status = request.newStatus().trim().toUpperCase(Locale.ROOT);
        if (!PACKAGE_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado de envío no válido.");
        }

        String location = request.location().trim();
        String notes = request.notes() == null || request.notes().isBlank()
                ? null
                : request.notes().trim();

        int updatedRows = jdbc.update("""
                UPDATE package_schema.packages
                   SET current_status = ?::package_schema.package_status,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """, status, packageId);
        if (updatedRows == 0) {
            throw new ResourceNotFoundException("Envío no encontrado.");
        }

        jdbc.update("""
                INSERT INTO package_schema.tracking_events
                    (package_id, status, location, description)
                VALUES (?, ?::package_schema.package_status, ?, ?)
                """, packageId, status, location, notes);

        return jdbc.query(PACKAGE_SELECT + " WHERE p.id = ?", PACKAGE_ROW_MAPPER, packageId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Envío no encontrado."));
    }

    public PackageResponse findByTrackingCode(String trackingCode) {
        if (trackingCode == null || trackingCode.isBlank()) {
            throw new IllegalArgumentException("El código de seguimiento es obligatorio.");
        }

        return jdbc.query(PACKAGE_SELECT + " WHERE UPPER(p.tracking_code) = ?",
                        PACKAGE_ROW_MAPPER, trackingCode.trim().toUpperCase(Locale.ROOT))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Envío no encontrado."));
    }

    static String generateTrackingCode() {
        return "NX-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 27).toUpperCase(Locale.ROOT);
    }

    private static PackageResponse mapPackage(ResultSet rs, int rowNum) throws SQLException {
        return new PackageResponse(
                rs.getObject("id", UUID.class), rs.getString("tracking_code"),
                rs.getString("sender_name"), rs.getString("sender_phone"),
                rs.getString("receiver_name"), rs.getString("receiver_phone"),
                rs.getString("origin_city"), rs.getString("destination_city"),
                rs.getString("destination_address"), rs.getBigDecimal("weight_kg"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
