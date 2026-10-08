package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.model.Category;
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
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe."));
    }

    public Category findBySlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);
        return categoryRepository.findBySlug(normalizedSlug)
                .orElseThrow(() -> new IllegalArgumentException("La categoría no existe."));
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
}
