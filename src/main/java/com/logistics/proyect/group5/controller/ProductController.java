package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.ProductRequest;
import com.logistics.proyect.group5.dto.ProductResponse;
import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.service.CategoryService;
import com.logistics.proyect.group5.service.CloudinaryService;
import com.logistics.proyect.group5.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<Product> products;
        if (search != null) {
            products = productService.searchByName(search);
        } else if (categoryId != null) {
            products = productService.findByCategory(categoryId);
        } else if (activeOnly) {
            products = productService.findActive();
        } else {
            products = productService.findAll();
        }
        return ResponseEntity.ok(products.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(productService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(productService.create(toEntity(request))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(toResponse(productService.update(id, toEntity(request))));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ProductResponse> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(productService.deactivate(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Product product = productService.findById(id);
        cloudinaryService.deleteImage(product.getImageUrl());
        cloudinaryService.deleteImage(product.getImageHoverUrl());
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    public ResponseEntity<ProductResponse> uploadImages(
            @PathVariable UUID id,
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "hoverImage", required = false) MultipartFile hoverImage) {
        Product current = productService.findById(id);
        String oldImageUrl = current.getImageUrl();
        String oldHoverImageUrl = current.getImageHoverUrl();

        String imageUrl = cloudinaryService.uploadImage(image);
        String hoverImageUrl = hoverImage == null || hoverImage.isEmpty()
                ? null
                : cloudinaryService.uploadImage(hoverImage);

        Product updated = productService.updateImages(id, imageUrl, hoverImageUrl);
        cloudinaryService.deleteImage(oldImageUrl);
        if (hoverImageUrl != null) {
            cloudinaryService.deleteImage(oldHoverImageUrl);
        }
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}/images/hover")
    public ResponseEntity<ProductResponse> deleteHoverImage(@PathVariable UUID id) {
        Product current = productService.findById(id);
        cloudinaryService.deleteImage(current.getImageHoverUrl());
        return ResponseEntity.ok(toResponse(productService.removeHoverImage(id)));
    }

    private Product toEntity(ProductRequest request) {
        return Product.builder()
                .sku(request.getSku())
                .name(request.getName())
                .slug(request.getSlug())
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock())
                .category(request.getCategoryId() == null
                        ? null
                        : com.logistics.proyect.group5.model.Category.builder()
                        .id(request.getCategoryId())
                        .build())
                .imageUrl(request.getImageUrl())
                .imageHoverUrl(request.getImageHoverUrl())
                .rating(request.getRating())
                .reviewsCount(request.getReviewsCount())
                .newProduct(request.getNewProduct() == null || request.getNewProduct())
                .active(request.getActive() == null || request.getActive())
                .build();
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
                .categoryId(product.getCategory() == null ? null : product.getCategory().getId())
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
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
