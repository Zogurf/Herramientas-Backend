package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.ProductResponse;
import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class PublicProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll() {
        List<ProductResponse> products = productService.findActive()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
        Product product;

        try {
            product = productService.findById(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        if (!product.isActive()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(product));
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .stockStatus(productService.stockStatus(product.getStock()))
                .categoryId(product.getCategory() == null
                        ? null : product.getCategory().getId())
                .categoryName(product.getCategory() == null
                        ? null : product.getCategory().getName())
                .imageUrl(product.getImageUrl())
                .imageHoverUrl(product.getImageHoverUrl())
                .rating(product.getRating())
                .reviewsCount(product.getReviewsCount())
                .newProduct(product.isNewProduct())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
