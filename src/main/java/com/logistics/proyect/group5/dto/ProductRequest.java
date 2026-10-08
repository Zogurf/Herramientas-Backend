package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank
    @Size(max = 50)
    private String sku;

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 220)
    private String slug;

    private String description;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer stock;

    private Integer categoryId;

    @NotBlank
    private String imageUrl;

    private String imageHoverUrl;

    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private BigDecimal rating;

    @Min(0)
    private Integer reviewsCount;

    private Boolean newProduct;
    private Boolean active;
}
