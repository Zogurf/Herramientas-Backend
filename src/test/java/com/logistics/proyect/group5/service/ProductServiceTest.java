package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.repository.ProductRepository;
import com.logistics.proyect.group5.repository.InventoryStatsProjection;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;

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
        productService = new ProductService(productRepository, categoryService, mock(CloudinaryService.class));
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
    void inventoryStatsReadsNamedAggregateValuesAndPreservesFilters() {
        var row = new SpelAwareProxyProjectionFactory().createProjection(
                InventoryStatsProjection.class, Map.of(
                        "totalProducts", 3L, "totalUnits", 15L,
                        "totalValue", new BigDecimal("250.50"),
                        "lowStockProducts", 1L, "outOfStockProducts", 1L));
        when(productRepository.inventoryStats("taladro", 2, true, "LOW")).thenReturn(row);

        var result = productService.inventoryStats(" taladro ", 2, true, " low ");

        assertEquals(3L, result.getTotalProducts());
        assertEquals(15L, result.getTotalUnits());
        assertEquals(new BigDecimal("250.50"), result.getTotalValue());
        assertEquals(1L, result.getLowStockProducts());
        assertEquals(1L, result.getOutOfStockProducts());
        verify(categoryService).findById(2);
        verify(productRepository).inventoryStats("taladro", 2, true, "LOW");
    }

    @Test
    void inventoryStatsSupportsEmptyInventory() {
        var row = new SpelAwareProxyProjectionFactory().createProjection(
                InventoryStatsProjection.class, Map.of(
                        "totalProducts", 0L, "totalUnits", 0L,
                        "totalValue", BigDecimal.ZERO,
                        "lowStockProducts", 0L, "outOfStockProducts", 0L));
        when(productRepository.inventoryStats(null, null, null, null)).thenReturn(row);

        var result = productService.inventoryStats(null, null, null, null);

        assertEquals(0L, result.getTotalProducts());
        assertEquals(0L, result.getTotalUnits());
        assertEquals(BigDecimal.ZERO, result.getTotalValue());
        assertEquals(0L, result.getLowStockProducts());
        assertEquals(0L, result.getOutOfStockProducts());
        verifyNoInteractions(categoryService);
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
