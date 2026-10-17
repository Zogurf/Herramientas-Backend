package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.dto.CategoryRequest;
import com.logistics.proyect.group5.dto.CategoryResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.repository.CategoryRepository;
import com.logistics.proyect.group5.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Integer id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String slug = resolveSlug(request.getSlug(), request.getName(), null);

        Category category = Category.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .active(request.getIsActive() == null || request.getIsActive())
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Integer id, CategoryRequest request) {
        Category category = findOrThrow(id);

        if (request.getName() != null && !request.getName().isBlank()) {
            category.setName(request.getName().trim());
        }
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String slug = SlugUtils.slugify(request.getSlug());
            if (!slug.isBlank() && !slug.equals(category.getSlug())) {
                category.setSlug(resolveSlug(slug, null, category.getId()));
            }
        }
        if (request.getDescription() != null) {
            category.setDescription(request.getDescription());
        }
        if (request.getIsActive() != null) {
            category.setActive(request.getIsActive());
        }

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Integer id) {
        Category category = findOrThrow(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException(
                    "No se puede eliminar la categoría '" + category.getName()
                            + "': hay productos que la están usando.");
        }
        categoryRepository.delete(category);
    }

    private Category findOrThrow(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada."));
    }

    /**
     * Genera el slug a partir del valor explícito o del nombre y garantiza su unicidad.
     */
    private String resolveSlug(String explicitSlug, String name, Integer currentId) {
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
        response.setIsActive(category.isActive());
        response.setCreatedAt(category.getCreatedAt());
        return response;
    }
}
