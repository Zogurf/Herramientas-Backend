package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private UUID id;
    private String sku;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String stockStatus;
    private Integer categoryId;
    private String categoryName;
    private String imageUrl;
    private String imageHoverUrl;
    private BigDecimal rating;
    private Integer reviewsCount;
    private boolean newProduct;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonProperty("isNew")
    public Boolean getIsNew() {
        return newProduct;
    }

    public void setIsNew(Boolean isNew) {
        if (isNew != null) {
            this.newProduct = isNew;
        }
    }

    @JsonProperty("isActive")
    public Boolean getIsActive() {
        return active;
    }

    public void setIsActive(Boolean isActive) {
        if (isActive != null) {
            this.active = isActive;
        }
    }
}