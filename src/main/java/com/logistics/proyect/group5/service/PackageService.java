package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.CreatePackageRequest;
import com.logistics.proyect.group5.dto.PackageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PackageService {
    private final JdbcTemplate jdbc;
    private final String originCity;

    public PackageService(JdbcTemplate jdbc,
            @Value("${app.packages.origin-city:Lima}") String originCity) {
        this.jdbc = jdbc;
        this.originCity = originCity;
    }

    @Transactional
    public PackageResponse create(CreatePackageRequest request, UUID senderId) {
        // PostgreSQL sequences allocate distinct values even for concurrent requests.
        return jdbc.queryForObject("""
            INSERT INTO package_schema.packages
            (tracking_code, sender_id, sender_name, sender_phone, recipient_name,
             recipient_phone, destination_address, origin_city, destination_city,
             weight_kg, description)
            VALUES ('NX-' || nextval('package_schema.tracking_code_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """, (rs, row) -> new PackageResponse(
                rs.getObject("id", UUID.class), rs.getString("tracking_code"),
                rs.getString("sender_name"), rs.getString("sender_phone"),
                rs.getString("recipient_name"), rs.getString("recipient_phone"),
                rs.getString("origin_city"), rs.getString("destination_city"),
                rs.getString("destination_address"), rs.getBigDecimal("weight_kg"),
                rs.getString("current_status"), rs.getString("description"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)),
            senderId, request.senderName().trim(), request.senderPhone().trim(),
            request.receiverName().trim(), request.receiverPhone().trim(),
            request.destinationAddress().trim(), originCity, request.destinationCity().trim(),
            request.weightKg(), request.description());
    }
}
