package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;

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
