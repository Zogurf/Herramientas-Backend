package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.CategoryRequest;
import com.logistics.proyect.group5.dto.CategoryResponse;
import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<Category> categories = search != null
                ? categoryService.searchByName(search)
                : activeOnly ? categoryService.findActive() : categoryService.findAll();
        return ResponseEntity.ok(categories.stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(@PathVariable Integer id) {
        return ResponseEntity.ok(toResponse(categoryService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Integer id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<CategoryResponse> deactivate(@PathVariable Integer id) {
        return ResponseEntity.ok(toResponse(categoryService.deactivate(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(category.getParent() == null ? null : category.getParent().getId())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}