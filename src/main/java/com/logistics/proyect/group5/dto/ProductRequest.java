package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    private Integer categoryId;

    private String imageUrl;

    private String imageHoverUrl;

    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private BigDecimal rating;

    @Min(0)
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