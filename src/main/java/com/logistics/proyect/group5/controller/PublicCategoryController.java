package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.CategoryResponse;
import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class PublicCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        List<CategoryResponse> categories = categoryService.findActive()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(categories);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentId(category.getParent() == null
                        ? null
                        : category.getParent().getId())
                .active(category.isActive())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
