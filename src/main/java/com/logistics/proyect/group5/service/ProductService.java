package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.ProductRequest;
import com.logistics.proyect.group5.dto.ProductResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.repository.CategoryRepository;
import com.logistics.proyect.group5.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CloudinaryService cloudinaryService;

    /** Catálogo público: solo productos activos. */
    @Transactional(readOnly = true)
    public List<ProductResponse> listActive() {
        return toResponses(productRepository.findByActiveTrueOrderByCreatedAtDesc());
    }

    /** Panel de administración: incluye productos inactivos. */
    @Transactional(readOnly = true)
    public List<ProductResponse> listAll() {
        return toResponses(productRepository.findAllByOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(findOrThrow(id), null);
    }

    @Transactional
    public ProductResponse create(ProductRequest request, MultipartFile file) {
        String imageUrl = resolveImage(request.getImageUrl(), file, true);

        validateCategory(request.getCategoryId());

        String sku = resolveSku(request.getSku(), request.getName(), null);
        String slug = resolveSlug(request.getSlug(), request.getName(), null);

        Product product = Product.builder()
                .sku(sku)
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .price(request.getPrice())
                .stock(request.getStock() != null ? request.getStock() : 0)
                .categoryId(request.getCategoryId())
                .imageUrl(imageUrl)
                .imageHoverUrl(request.getImageHoverUrl())
                .rating(new BigDecimal("5.00"))
                .reviewsCount(0)
                .newArrival(request.getIsNew() == null || request.getIsNew())
                .active(request.getIsActive() == null || request.getIsActive())
                .build();

        return toResponse(productRepository.save(product), null);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request, MultipartFile file) {
        Product product = findOrThrow(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            product.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }
        if (request.getCategoryId() != null) {
            validateCategory(request.getCategoryId());
            product.setCategoryId(request.getCategoryId());
        }
        if (request.getImageHoverUrl() != null) {
            product.setImageHoverUrl(request.getImageHoverUrl());
        }
        if (request.getIsNew() != null) {
            product.setNewArrival(request.getIsNew());
        }
        if (request.getIsActive() != null) {
            product.setActive(request.getIsActive());
        }
        if (request.getSku() != null && !request.getSku().isBlank()
                && !request.getSku().trim().equals(product.getSku())) {
            product.setSku(resolveSku(request.getSku(), null, product.getId()));
        }
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String slug = SlugUtils.slugify(request.getSlug());
            if (!slug.isBlank() && !slug.equals(product.getSlug())) {
                product.setSlug(resolveSlug(slug, null, product.getId()));
            }
        }

        String imageUrl = resolveImage(request.getImageUrl(), file, false);
        if (imageUrl != null) {
            product.setImageUrl(imageUrl);
        }

        return toResponse(productRepository.save(product), null);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findOrThrow(id);
        productRepository.delete(product);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado."));
    }

    private void validateCategory(Integer categoryId) {
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw new IllegalArgumentException("La categoría indicada no existe.");
        }
    }

    /**
     * Devuelve la URL de la imagen. Si se sube un archivo, tiene prioridad sobre la URL enviada.
     *
     * @param required si {@code true} exige que exista una imagen (columna {@code image_url} es NOT NULL)
     */
    private String resolveImage(String imageUrl, MultipartFile file, boolean required) {
        if (file != null && !file.isEmpty()) {
            return cloudinaryService.uploadImage(file);
        }
        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl.trim();
        }
        if (required) {
            throw new IllegalArgumentException("La imagen del producto es obligatoria (archivo o imageUrl).");
        }
        return null;
    }

    private String resolveSku(String explicitSku, String name, UUID currentId) {
        String base = (explicitSku != null && !explicitSku.isBlank())
                ? explicitSku.trim().toUpperCase()
                : generateSku(name);

        String candidate = base;
        int suffix = 2;
        while (true) {
            var existing = productRepository.findBySku(candidate);
            if (existing.isEmpty() || (currentId != null && existing.get().getId().equals(currentId))) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private String generateSku(String name) {
        String prefix = SlugUtils.slugify(name);
        prefix = prefix.isBlank() ? "NX" : prefix.replace("-", "").toUpperCase();
        if (prefix.length() > 8) {
            prefix = prefix.substring(0, 8);
        }
        return "NX-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String resolveSlug(String explicitSlug, String name, UUID currentId) {
        String base = (explicitSlug != null && !explicitSlug.isBlank())
                ? SlugUtils.slugify(explicitSlug)
                : SlugUtils.slugify(name);

        if (base.isBlank()) {
            base = "producto";
        }

        String candidate = base;
        int suffix = 2;
        while (true) {
            var existing = productRepository.findBySlug(candidate);
            if (existing.isEmpty() || (currentId != null && existing.get().getId().equals(currentId))) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private List<ProductResponse> toResponses(List<Product> products) {
        Map<Integer, String> categoryNames = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        return products.stream()
                .map(product -> toResponse(product, categoryNames))
                .toList();
    }

    private ProductResponse toResponse(Product product, Map<Integer, String> categoryNames) {
        String categoryName = null;
        if (product.getCategoryId() != null) {
            if (categoryNames != null) {
                categoryName = categoryNames.get(product.getCategoryId());
            }
            if (categoryName == null) {
                categoryName = categoryRepository.findById(product.getCategoryId())
                        .map(Category::getName)
                        .orElse(null);
            }
        }

        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setSlug(product.getSlug());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
        response.setCategoryId(product.getCategoryId());
        response.setCategoryName(categoryName);
        response.setImageUrl(product.getImageUrl());
        response.setImageHoverUrl(product.getImageHoverUrl());
        response.setRating(product.getRating());
        response.setReviewsCount(product.getReviewsCount());
        response.setIsNew(product.isNewArrival());
        response.setIsActive(product.isActive());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }
}
