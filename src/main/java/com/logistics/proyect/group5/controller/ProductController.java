package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.ProductRequest;
import com.logistics.proyect.group5.dto.ProductResponse;
import com.logistics.proyect.group5.dto.InventoryStatsResponse;
import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.service.CategoryService;
import com.logistics.proyect.group5.service.CloudinaryService;
import com.logistics.proyect.group5.service.InventoryExportService;
import com.logistics.proyect.group5.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CloudinaryService cloudinaryService;
    private final InventoryExportService inventoryExportService;

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

    @GetMapping("/inventory")
    public ResponseEntity<Page<ProductResponse>> searchInventory(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("La paginación no es válida.");
        }
        String sortField = switch (sortBy) {
            case "name", "sku", "price", "stock", "createdAt", "updatedAt" -> sortBy;
            default -> throw new IllegalArgumentException("El campo de ordenamiento no es válido.");
        };
        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new IllegalArgumentException("La dirección de ordenamiento no es válida."));
        Page<ProductResponse> response = productService.searchInventory(
                        search, categoryId, active, stockStatus,
                        PageRequest.of(page, size, Sort.by(sortDirection, sortField)))
                .map(this::toResponse);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/inventory/stats")
    public ResponseEntity<InventoryStatsResponse> inventoryStats(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String stockStatus) {
        return ResponseEntity.ok(productService.inventoryStats(search, categoryId, active, stockStatus));
    }

    @GetMapping("/inventory/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String stockStatus) {
        return download(
                inventoryExportService.exportExcel(search, categoryId, active, stockStatus),
                "inventario.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/inventory/export.pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String stockStatus) {
        return download(
                inventoryExportService.exportPdf(search, categoryId, active, stockStatus),
                "inventario.pdf",
                MediaType.APPLICATION_PDF_VALUE);
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

    private ResponseEntity<byte[]> download(byte[] content, String filename, String contentType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
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
