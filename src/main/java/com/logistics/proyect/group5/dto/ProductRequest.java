package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Datos de entrada para crear o actualizar un producto.
 * Se enlaza tanto desde {@code multipart/form-data} como desde JSON.
 */
@Data
public class ProductRequest {

    @NotBlank(message = "El nombre del producto es obligatorio")
    @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
    private String name;

    @Size(max = 50, message = "El SKU no puede superar 50 caracteres")
    private String sku;

    @Size(max = 220, message = "El slug no puede superar 220 caracteres")
    private String slug;

    private String description;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    private BigDecimal price;

    @PositiveOrZero(message = "El stock no puede ser negativo")
    private Integer stock;

    private Integer categoryId;

    private String imageUrl;

    private String imageHoverUrl;

    private Boolean isNew;

    private Boolean isActive;
}
