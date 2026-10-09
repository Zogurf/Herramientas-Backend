package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryService);
    }

    @Test
    void createNormalizesSkuAndGeneratesSlug() {
        Product product = Product.builder()
                .sku("  kb-001 ")
                .name("  Teclado Gamer  ")
                .price(BigDecimal.valueOf(120))
                .stock(8)
                .imageUrl("https://example.com/image.png")
                .build();

        when(productRepository.findBySku("KB-001")).thenReturn(Optional.empty());
        when(productRepository.findBySlug("teclado-gamer")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product saved = productService.create(product);

        assertEquals("KB-001", saved.getSku());
        assertEquals("Teclado Gamer", saved.getName());
        assertEquals("teclado-gamer", saved.getSlug());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(saved.getRating()));
        assertEquals(0, saved.getReviewsCount());
        verify(productRepository).save(product);
    }

    @Test
    void createRejectsDuplicateSku() {
        Product existing = Product.builder().id(UUID.randomUUID()).sku("KB-001").build();
        Product product = Product.builder()
                .sku("kb-001")
                .name("Teclado")
                .price(BigDecimal.TEN)
                .stock(1)
                .imageUrl("https://example.com/image.png")
                .build();

        when(productRepository.findBySku("KB-001")).thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(product)
        );

        assertEquals("El SKU del producto ya está registrado.", exception.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsNegativeStock() {
        Product product = Product.builder()
                .sku("KB-001")
                .name("Teclado")
                .price(BigDecimal.TEN)
                .stock(-1)
                .imageUrl("https://example.com/image.png")
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> productService.create(product)
        );

        assertEquals("El stock debe ser mayor o igual a cero.", exception.getMessage());
        verifyNoInteractions(productRepository);
    }

    @Test
    void stockStatusUsesInventoryThresholds() {
        assertEquals("OUT", productService.stockStatus(0));
        assertEquals("OUT", productService.stockStatus(null));
        assertEquals("LOW", productService.stockStatus(1));
        assertEquals("LOW", productService.stockStatus(10));
        assertEquals("OPTIMAL", productService.stockStatus(11));
    }
}
