package com.logistics.proyect.group5.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registra los eventos de rastreo de un pedido en la línea de tiempo.
 */
@Entity
@Table(name = "tracking_events", schema = "store_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 50)
    private String status; // Ej: PENDIENTE, EN_TRANSITO, ENTREGADO

    @Column(length = 200)
    private String location; // Ej: Centro de distribución Lima Norte

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "event_date", nullable = false, updatable = false)
    private LocalDateTime eventDate;

    @PrePersist
    protected void onCreate() {
        if (eventDate == null) {
            eventDate = LocalDateTime.now();
        }
    }
}