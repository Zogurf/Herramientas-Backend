package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.dto.ProductRequest;
import com.logistics.proyect.group5.dto.ProductResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.logistics.proyect.group5.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.logistics.proyect.group5.dto.InventoryStatsResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final CloudinaryService cloudinaryService;

    public List<ProductResponse> listActive() {
        return toResponses(productRepository.findByActiveTrueOrderByCreatedAtDesc());
    }

    public List<ProductResponse> listAll() {
        return toResponses(productRepository.findAllByOrderByCreatedAtDesc());
    }

    public ProductResponse get(UUID id) {
        return toResponse(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id)), null);
    }

    @Transactional
    public ProductResponse create(ProductRequest request, MultipartFile file) {
        String name = normalizeText(request.getName());
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio debe ser mayor o igual a cero.");
        }
        if (request.getStock() != null && request.getStock() < 0) {
            throw new IllegalArgumentException("El stock debe ser mayor o igual a cero.");
        }

        String imageUrl = resolveImage(request.getImageUrl(), file, true);
        String sku = resolveAutomaticSku(request.getSku(), name, null);
        String slug = resolveAutomaticSlug(request.getSlug(), name, null);

        Product product = Product.builder()
                .sku(sku)
                .name(name)
                .slug(slug)
                .description(normalizeOptionalText(request.getDescription()))
                .price(request.getPrice())
                .stock(request.getStock() == null ? 0 : request.getStock())
                .category(resolveCategoryId(request.getCategoryId()))
                .imageUrl(imageUrl)
                .imageHoverUrl(normalizeOptionalText(request.getImageHoverUrl()))
                .rating(request.getRating())
                .reviewsCount(request.getReviewsCount())
                .newProduct(request.getNewProduct() == null || request.getNewProduct())
                .active(request.getActive() == null || request.getActive())
                .build();

        validateProduct(product, true);
        applyDefaults(product);

        return toResponse(productRepository.save(product), null);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request, MultipartFile file) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));

        if (request.getName() != null) {
            String name = normalizeText(request.getName());
            if (name.isBlank()) {
                throw new IllegalArgumentException("El nombre del producto es obligatorio.");
            }
            product.setName(name);
        }

        if (request.getDescription() != null) {
            product.setDescription(normalizeOptionalText(request.getDescription()));
        }

        if (request.getPrice() != null) {
            if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El precio no puede ser negativo.");
            }
            product.setPrice(request.getPrice());
        }

        if (request.getStock() != null) {
            if (request.getStock() < 0) {
                throw new IllegalArgumentException("El stock no puede ser negativo.");
            }
            product.setStock(request.getStock());
        }

        if (request.getCategoryId() != null) {
            product.setCategory(resolveCategoryId(request.getCategoryId()));
        }

        if (request.getSku() != null && !request.getSku().isBlank()) {
            product.setSku(resolveAutomaticSku(request.getSku(), product.getName(), id));
        }

        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            product.setSlug(resolveAutomaticSlug(request.getSlug(), product.getName(), id));
        }

        if (request.getImageHoverUrl() != null) {
            product.setImageHoverUrl(normalizeOptionalText(request.getImageHoverUrl()));
        }

        if (request.getRating() != null) {
            product.setRating(request.getRating());
        }

        if (request.getReviewsCount() != null) {
            product.setReviewsCount(request.getReviewsCount());
        }

        if (request.getNewProduct() != null) {
            product.setNewProduct(request.getNewProduct());
        }

        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }

        if ((file != null && !file.isEmpty())
                || (request.getImageUrl() != null && !request.getImageUrl().isBlank())) {
            product.setImageUrl(resolveImage(request.getImageUrl(), file, false));
        }

        validateProduct(product, true);

        return toResponse(productRepository.save(product), null);
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public List<Product> findActive() {
        return productRepository.findByActiveTrue();
    }

    public List<Product> searchByName(String name) {
        String normalizedName = normalizeText(name);
        if (normalizedName.isBlank()) {
            return findAll();
        }
        return productRepository.findByNameContainingIgnoreCase(normalizedName);
    }

    public List<Product> findByCategory(Integer categoryId) {
        categoryService.findById(categoryId);
        return productRepository.findByCategory_Id(categoryId);
    }

    public Page<Product> searchInventory(
            String search,
            Integer categoryId,
            Boolean active,
            String stockStatus,
            Pageable pageable) {
        if (categoryId != null) {
            categoryService.findById(categoryId);
        }
        return productRepository.searchInventory(
                normalizeOptionalText(search),
                categoryId,
                active,
                normalizeStockStatus(stockStatus),
                pageable);
    }

    public InventoryStatsResponse inventoryStats(
            String search,
            Integer categoryId,
            Boolean active,
            String stockStatus) {
        if (categoryId != null) {
            categoryService.findById(categoryId);
        }
        Object[] values = productRepository.inventoryStats(
                normalizeOptionalText(search),
                categoryId,
                active,
                normalizeStockStatus(stockStatus));
        return InventoryStatsResponse.builder()
                .totalProducts(((Number) values[0]).longValue())
                .totalUnits(((Number) values[1]).longValue())
                .totalValue((BigDecimal) values[2])
                .lowStockProducts(((Number) values[3]).longValue())
                .outOfStockProducts(((Number) values[4]).longValue())
                .build();
    }

    public Product findById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe."));
    }

    public Product findBySku(String sku) {
        return productRepository.findBySku(normalizeSku(sku))
                .orElseThrow(() -> new IllegalArgumentException("El producto no existe."));
    }

    @Transactional
    public Product create(Product product) {
        validateProduct(product, true);

        String normalizedSku = normalizeSku(product.getSku());
        String normalizedSlug = resolveSlug(product.getName(), product.getSlug());
        ensureSkuAvailable(normalizedSku, null);
        ensureSlugAvailable(normalizedSlug, null);

        product.setSku(normalizedSku);
        product.setName(normalizeText(product.getName()));
        product.setSlug(normalizedSlug);
        product.setDescription(normalizeOptionalText(product.getDescription()));
        product.setCategory(resolveCategory(product.getCategory()));
        product.setImageUrl(normalizeText(product.getImageUrl()));
        product.setImageHoverUrl(normalizeOptionalText(product.getImageHoverUrl()));
        applyDefaults(product);

        return productRepository.save(product);
    }

    @Transactional
    public Product update(UUID id, Product product) {
        validateProduct(product, false);

        Product existing = findById(id);
        String normalizedSku = normalizeSku(product.getSku());
        String normalizedSlug = resolveSlug(product.getName(), product.getSlug());
        ensureSkuAvailable(normalizedSku, id);
        ensureSlugAvailable(normalizedSlug, id);

        existing.setSku(normalizedSku);
        existing.setName(normalizeText(product.getName()));
        existing.setSlug(normalizedSlug);
        existing.setDescription(normalizeOptionalText(product.getDescription()));
        existing.setPrice(product.getPrice());
        existing.setStock(product.getStock());
        existing.setCategory(resolveCategory(product.getCategory()));

        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            existing.setImageUrl(normalizeText(product.getImageUrl()));
        }
        existing.setImageHoverUrl(normalizeOptionalText(product.getImageHoverUrl()));
        existing.setRating(product.getRating());
        existing.setReviewsCount(product.getReviewsCount());
        existing.setNewProduct(product.isNewProduct());
        existing.setActive(product.isActive());

        return productRepository.save(existing);
    }

    @Transactional
    public Product deactivate(UUID id) {
        Product product = findById(id);
        product.setActive(false);
        return productRepository.save(product);
    }

    @Transactional
    public Product updateImages(UUID id, String imageUrl, String imageHoverUrl) {
        Product product = findById(id);
        if (imageUrl != null && !imageUrl.isBlank()) {
            product.setImageUrl(imageUrl);
        }
        if (imageHoverUrl != null && !imageHoverUrl.isBlank()) {
            product.setImageHoverUrl(imageHoverUrl);
        }
        return productRepository.save(product);
    }

    @Transactional
    public Product removeHoverImage(UUID id) {
        Product product = findById(id);
        product.setImageHoverUrl(null);
        return productRepository.save(product);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = findById(id);
        productRepository.delete(product);
    }

    public String stockStatus(Integer stock) {
        if (stock == null || stock == 0) {
            return "OUT";
        }
        if (stock <= 10) {
            return "LOW";
        }
        return "OPTIMAL";
    }

    private Category resolveCategoryId(Integer categoryId) {
        return categoryId == null ? null : categoryService.findById(categoryId);
    }

    private String resolveImage(String imageUrl, MultipartFile file, boolean required) {
        if (file != null && !file.isEmpty()) {
            return cloudinaryService.uploadImage(file);
        }

        if (imageUrl != null && !imageUrl.isBlank()) {
            return imageUrl.trim();
        }

        if (required) {
            throw new IllegalArgumentException("La imagen principal del producto es obligatoria.");
        }

        return null;
    }

    private String resolveAutomaticSku(String sku, String name, UUID currentId) {
        String normalized = normalizeSku(sku);

        if (normalized.isBlank()) {
            String prefix = slugify(name).replace("-", "").toUpperCase(Locale.ROOT);
            if (prefix.isBlank()) {
                prefix = "PRODUCT";
            }
            prefix = prefix.substring(0, Math.min(prefix.length(), 8));
            normalized = "NX-" + prefix + "-"
                    + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        }

        String base = normalized;
        int suffix = 2;

        while (true) {
            var existing = productRepository.findBySku(normalized);
            if (existing.isEmpty() || existing.get().getId().equals(currentId)) {
                return normalized;
            }
            String ending = "-" + suffix++;
            normalized = base.substring(0, Math.min(base.length(), 50 - ending.length())) + ending;
        }
    }

    private String resolveAutomaticSlug(String slug, String name, UUID currentId) {
        String normalized = slug == null || slug.isBlank()
                ? slugify(name)
                : slugify(slug);

        if (normalized.isBlank()) {
            normalized = "producto";
        }

        String base = normalized;
        int suffix = 2;

        while (true) {
            var existing = productRepository.findBySlug(normalized);
            if (existing.isEmpty() || existing.get().getId().equals(currentId)) {
                return normalized;
            }
            String ending = "-" + suffix++;
            normalized = base.substring(0, Math.min(base.length(), 220 - ending.length())) + ending;
        }
    }

    private List<ProductResponse> toResponses(List<Product> products) {
        Map<Integer, String> categoryNames = categoryService.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        return products.stream()
                .map(product -> toResponse(product, categoryNames))
                .toList();
    }

    private ProductResponse toResponse(Product product, Map<Integer, String> categoryNames) {
        Integer categoryId = product.getCategoryId();

        String categoryName = null;
        if (categoryId != null) {
            categoryName = categoryNames == null
                    ? categoryService.findById(categoryId).getName()
                    : categoryNames.get(categoryId);
        }

        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .price(product.getPrice())
                .stock(product.getStock())
                .stockStatus(stockStatus(product.getStock()))
                .categoryId(categoryId)
                .categoryName(categoryName)
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

    private void validateProduct(Product product, boolean requireImage) {
        if (product == null) {
            throw new IllegalArgumentException("El producto es obligatorio.");
        }

        String name = normalizeText(product.getName());
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio.");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("El nombre del producto no puede superar 200 caracteres.");
        }
        if (normalizeSku(product.getSku()).isBlank()) {
            throw new IllegalArgumentException("El SKU del producto es obligatorio.");
        }
        if (normalizeSku(product.getSku()).length() > 50) {
            throw new IllegalArgumentException("El SKU no puede superar 50 caracteres.");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio debe ser mayor o igual a cero.");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("El stock debe ser mayor o igual a cero.");
        }
        if (requireImage && normalizeText(product.getImageUrl()).isBlank()) {
            throw new IllegalArgumentException("La imagen principal del producto es obligatoria.");
        }
        if (product.getRating() != null
                && (product.getRating().compareTo(BigDecimal.ZERO) < 0
                || product.getRating().compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw new IllegalArgumentException("La valoración debe estar entre 0 y 5.");
        }
        if (product.getReviewsCount() != null && product.getReviewsCount() < 0) {
            throw new IllegalArgumentException("La cantidad de reseñas no puede ser negativa.");
        }
    }

    private Category resolveCategory(Category category) {
        if (category == null || category.getId() == null) {
            return null;
        }
        return categoryService.findById(category.getId());
    }

    private void ensureSkuAvailable(String sku, UUID productId) {
        productRepository.findBySku(sku).ifPresent(existing -> {
            if (!existing.getId().equals(productId)) {
                throw new IllegalArgumentException("El SKU del producto ya está registrado.");
            }
        });
    }

    private void ensureSlugAvailable(String slug, UUID productId) {
        productRepository.findBySlug(slug).ifPresent(existing -> {
            if (!existing.getId().equals(productId)) {
                throw new IllegalArgumentException("El slug del producto ya está registrado.");
            }
        });
    }

    private void applyDefaults(Product product) {
        if (product.getRating() == null) {
            product.setRating(BigDecimal.valueOf(5));
        }
        if (product.getReviewsCount() == null) {
            product.setReviewsCount(0);
        }
    }

    private String resolveSlug(String name, String slug) {
        String normalizedSlug = normalizeSlug(slug);
        return normalizedSlug.isBlank() ? slugify(name) : normalizedSlug;
    }

    private String normalizeSku(String sku) {
        return sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStockStatus(String stockStatus) {
        if (stockStatus == null || stockStatus.isBlank()) {
            return null;
        }
        String normalized = stockStatus.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OUT", "LOW", "OPTIMAL").contains(normalized)) {
            throw new IllegalArgumentException("El estado de stock no es válido.");
        }
        return normalized;
    }

    private String normalizeSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        return value == null ? null : value.trim();
    }

    private String slugify(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
