package com.logistics.proyect.group5.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Producto del catálogo. Mapea {@code store_schema.products}.
 * <p>
 * Nota: la columna de baja lógica es {@code is_active}; el campo {@code active}
 * evita el prefijo {@code is} (que confunde a Lombok y a Jackson).
 */
@Entity
@Table(name = "products", schema = "store_schema")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;

    @Column(name = "image_hover_url", columnDefinition = "text")
    private String imageHoverUrl;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = new BigDecimal("5.00");

    @Column(name = "reviews_count")
    @Builder.Default
    private Integer reviewsCount = 0;

    @Column(name = "is_new")
    @Builder.Default
    private boolean newArrival = true;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (stock == null) {
            stock = 0;
        }
        if (rating == null) {
            rating = new BigDecimal("5.00");
        }
        if (reviewsCount == null) {
            reviewsCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
