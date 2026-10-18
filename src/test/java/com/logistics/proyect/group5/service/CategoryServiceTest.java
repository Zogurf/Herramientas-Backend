package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.model.Category;
import com.logistics.proyect.group5.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private com.logistics.proyect.group5.repository.ProductRepository productRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, productRepository);
    }

    @Test
    void cannotDeleteCategoryUsedByInventory() {
        Category category = Category.builder().id(1).name("Herramientas").build();
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(1)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> categoryService.delete(1));
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void publicCatalogGeneratesUniqueSlug() {
        var request = com.logistics.proyect.group5.dto.CategoryRequest.builder()
                .name("Herramientas").build();
        when(categoryRepository.findBySlug("herramientas"))
                .thenReturn(Optional.of(Category.builder().id(1).slug("herramientas").build()));
        when(categoryRepository.findBySlug("herramientas-2")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("herramientas-2", categoryService.create(request).getSlug());
    }

    @Test
    void createNormalizesNameAndGeneratesSlug() {
        Category category = Category.builder()
                .name("  Teclados   Gamer  ")
                .description("  Mecánicos  ")
                .build();

        when(categoryRepository.findBySlug("teclados-gamer")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category saved = categoryService.create(category);

        assertEquals("Teclados Gamer", saved.getName());
        assertEquals("teclados-gamer", saved.getSlug());
        assertEquals("  Mecánicos  ", saved.getDescription());
        assertTrue(saved.isActive());
        verify(categoryRepository).save(category);
    }

    @Test
    void createRejectsDuplicateSlug() {
        Category existing = Category.builder().id(1).slug("teclados").build();
        Category category = Category.builder().name("Teclados").slug("teclados").build();
        when(categoryRepository.findBySlug("teclados")).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.create(category)
        );

        assertEquals("El slug de la categoría ya está registrado.", exception.getMessage());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateRejectsCategoryCycle() {
        Category current = Category.builder().id(1).name("Hardware").slug("hardware").build();
        Category descendant = Category.builder().id(2).name("Gaming").slug("gaming").parent(current).build();
        Category update = Category.builder().name("Hardware").slug("hardware").parent(descendant).build();

        when(categoryRepository.findById(1)).thenReturn(Optional.of(current));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(descendant));
        when(categoryRepository.findBySlug("hardware")).thenReturn(Optional.of(current));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.update(1, update)
        );

        assertTrue(exception.getMessage().contains("no puede ser su propia categoría padre"));
        verify(categoryRepository, never()).save(any());
    }
}
