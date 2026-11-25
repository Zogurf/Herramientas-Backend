package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductRequest {

    // Nombre y precio son obligatorios al crear, pero opcionales al actualizar.
    // Esa regla de presencia la aplica ProductService según la operación.
    @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
    private String name;

    @Size(max = 50, message = "El SKU no puede superar 50 caracteres")
    private String sku;

    @Size(max = 220, message = "El slug no puede superar 220 caracteres")
    private String slug;

    @Size(max = 10000, message = "La descripción no puede superar 10000 caracteres")
    private String description;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal price;

    @Min(value = 0, message = "El stock no puede ser negativo")
    @Max(value = 2147483647, message = "El stock excede el máximo permitido")
    private Integer stock;

    @Positive(message = "La categoría no es válida")
    private Integer categoryId;

    /**
     * Permite distinguir "categoryId omitido" de "categoryId: null" (quitar la
     * categoría) en las actualizaciones parciales.
     */
    @Builder.Default
    private boolean categoryIdProvided = false;

    @JsonSetter("categoryId")
    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
        this.categoryIdProvided = true;
    }

    @Size(max = 2048, message = "La URL de la imagen no puede superar 2048 caracteres")
    private String imageUrl;

    @Size(max = 2048, message = "La URL de la imagen hover no puede superar 2048 caracteres")
    private String imageHoverUrl;

    @DecimalMin("0.0")
    @DecimalMax("5.0")
    @Digits(integer = 1, fraction = 2, message = "La valoración debe tener como máximo 1 entero y 2 decimales")
    private BigDecimal rating;

    @Min(0)
    @Max(1000000)
    private Integer reviewsCount;

    @JsonAlias("isNew")
    private Boolean newProduct;

    @JsonAlias("isActive")
    private Boolean active;

    public Boolean getIsNew() {
        return newProduct;
    }

    public void setIsNew(Boolean isNew) {
        this.newProduct = isNew;
    }

    public Boolean getIsActive() {
        return active;
    }

    public void setIsActive(Boolean isActive) {
        this.active = isActive;
    }
}
