package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.dto.CategoryRequest;
import com.logistics.proyect.group5.dto.CategoryResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import com.logistics.proyect.group5.repository.ProductRepository;
import com.logistics.proyect.group5.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public List<Category> findActive() {
        return categoryRepository.findByActiveTrue();
    }

    public List<Category> searchByName(String name) {
        String normalizedName = normalizeName(name);
        if (normalizedName.isBlank()) {
            return findAll();
        }
        return categoryRepository.findByNameContainingIgnoreCase(normalizedName);
    }

    public Category findById(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada."));
    }

    public Category findBySlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        return categoryRepository.findBySlug(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada."));
    }

    @Transactional
    public Category create(Category category) {
        validateCategory(category);

        String normalizedSlug = resolveSlug(category.getName(), category.getSlug());
        ensureSlugAvailable(normalizedSlug, null);

        category.setName(normalizeName(category.getName()));
        category.setSlug(normalizedSlug);
        category.setParent(resolveParent(category.getParent()));
        category.setActive(true);

        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Integer id, Category category) {
        validateCategory(category);

        Category existing = findById(id);
        String normalizedSlug = resolveSlug(category.getName(), category.getSlug());
        ensureSlugAvailable(normalizedSlug, id);

        Category parent = resolveParent(category.getParent());
        validateParentHierarchy(existing, parent);

        existing.setName(normalizeName(category.getName()));
        existing.setSlug(normalizedSlug);
        existing.setDescription(normalizeOptionalText(category.getDescription()));
        existing.setParent(parent);
        existing.setActive(category.isActive());

        return categoryRepository.save(existing);
    }

    @Transactional
    public Category deactivate(Integer id) {
        Category category = findById(id);
        category.setActive(false);
        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(Integer id) {
        Category category = findById(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException("No se puede eliminar la categoría: hay productos que la están usando.");
        }
        if (categoryRepository.existsByParent_Id(id)) {
            throw new IllegalArgumentException("No se puede eliminar la categoría: tiene subcategorías asociadas.");
        }
        categoryRepository.delete(category);
    }

    private void validateCategory(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("La categoría es obligatoria.");
        }

        String name = normalizeName(category.getName());
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio.");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("El nombre de la categoría no puede superar 100 caracteres.");
        }
    }

    private Category resolveParent(Category parent) {
        if (parent == null || parent.getId() == null) {
            return null;
        }
        return findById(parent.getId());
    }

    private void validateParentHierarchy(Category category, Category parent) {
        Category current = parent;
        while (current != null) {
            if (category.getId().equals(current.getId())) {
                throw new IllegalArgumentException("Una categoría no puede ser su propia categoría padre ni un descendiente.");
            }
            current = current.getParent();
        }
    }

    private void ensureSlugAvailable(String slug, Integer categoryId) {
        categoryRepository.findBySlug(slug).ifPresent(existing -> {
            if (!existing.getId().equals(categoryId)) {
                throw new IllegalArgumentException("El slug de la categoría ya está registrado.");
            }
        });
    }

    private String resolveSlug(String name, String slug) {
        String normalizedSlug = normalizeSlug(slug);
        return normalizedSlug.isBlank() ? slugify(name) : normalizedSlug;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private String normalizeOptionalText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeSlug(String slug) {
        return slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private String slugify(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        // Endpoint público: no expone categorías desactivadas.
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Integer id) {
        Category category = findOrThrow(id);
        if (!category.isActive()) {
            throw new ResourceNotFoundException("Categoría no encontrada.");
        }
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = normalizeName(request.getName());
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio.");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("El nombre de la categoría no puede superar 100 caracteres.");
        }

        String description = normalizeOptionalText(request.getDescription());
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("La descripción no puede superar 2000 caracteres.");
        }

        Category parent = request.getParentId() == null ? null : findById(request.getParentId());
        String slug = resolveAutomaticSlug(request.getSlug(), name, null);

        Category category = Category.builder()
                .name(name)
                .slug(slug)
                .description(description)
                .parent(parent)
                .active(request.getActive() == null || request.getActive())
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Integer id, CategoryRequest request) {
        Category category = findOrThrow(id);

        if (request.getName() != null) {
            String name = normalizeName(request.getName());
            if (name.isBlank()) {
                throw new IllegalArgumentException("El nombre de la categoría es obligatorio.");
            }
            if (name.length() > 100) {
                throw new IllegalArgumentException("El nombre de la categoría no puede superar 100 caracteres.");
            }
            category.setName(name);
        }

        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String slug = SlugUtils.slugify(request.getSlug());
            if (!slug.isBlank() && !slug.equals(category.getSlug())) {
                category.setSlug(resolveAutomaticSlug(slug, null, category.getId()));
            }
        }

        if (request.getDescription() != null) {
            String description = normalizeOptionalText(request.getDescription());
            if (description != null && description.length() > 2000) {
                throw new IllegalArgumentException("La descripción no puede superar 2000 caracteres.");
            }
            category.setDescription(description);
        }

        if (request.isParentIdProvided()) {
            Category parent = request.getParentId() == null ? null : findById(request.getParentId());
            validateParentHierarchy(category, parent);
            category.setParent(parent);
        }

        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }

        return toResponse(categoryRepository.save(category));
    }


    private Category findOrThrow(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada."));
    }

    /**
     * Genera el slug a partir del valor explícito o del nombre y garantiza su unicidad.
     */
    private String resolveAutomaticSlug(String explicitSlug, String name, Integer currentId) {
        String base = (explicitSlug != null && !explicitSlug.isBlank())
                ? SlugUtils.slugify(explicitSlug)
                : SlugUtils.slugify(name);

        if (base.isBlank()) {
            base = "categoria";
        }

        String candidate = base;
        int suffix = 2;
        while (true) {
            var existing = categoryRepository.findBySlug(candidate);
            if (existing.isEmpty() || existing.get().getId().equals(currentId)) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
        }
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setSlug(category.getSlug());
        response.setDescription(category.getDescription());
        response.setParentId(category.getParent() == null ? null : category.getParent().getId());
        response.setIsActive(category.isActive());
        response.setCreatedAt(category.getCreatedAt());
        return response;
    }
}
