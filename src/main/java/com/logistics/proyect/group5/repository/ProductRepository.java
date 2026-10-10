package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Product> findByCategory_Id(Integer categoryId);

    boolean existsByCategory_Id(Integer categoryId);

    List<Product> findByActiveTrue();

    List<Product> findByNameContainingIgnoreCase(String name);

    @Query("""
            SELECT p
            FROM Product p
            WHERE (:search IS NULL OR :search = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:active IS NULL OR p.active = :active)
              AND (:stockStatus IS NULL
                OR (:stockStatus = 'OUT' AND p.stock = 0)
                OR (:stockStatus = 'LOW' AND p.stock > 0 AND p.stock <= 10)
                OR (:stockStatus = 'OPTIMAL' AND p.stock > 10))
            """)
    Page<Product> searchInventory(
            @Param("search") String search,
            @Param("categoryId") Integer categoryId,
            @Param("active") Boolean active,
            @Param("stockStatus") String stockStatus,
            Pageable pageable);

    @Query("""
            SELECT COUNT(p), COALESCE(SUM(p.stock), 0),
                   COALESCE(SUM(p.price * p.stock), 0),
                   SUM(CASE WHEN p.stock > 0 AND p.stock <= 10 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN p.stock = 0 THEN 1 ELSE 0 END)
            FROM Product p
            WHERE (:search IS NULL OR :search = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:active IS NULL OR p.active = :active)
              AND (:stockStatus IS NULL
                OR (:stockStatus = 'OUT' AND p.stock = 0)
                OR (:stockStatus = 'LOW' AND p.stock > 0 AND p.stock <= 10)
                OR (:stockStatus = 'OPTIMAL' AND p.stock > 10))
            """)
    Object[] inventoryStats(
            @Param("search") String search,
            @Param("categoryId") Integer categoryId,
            @Param("active") Boolean active,
            @Param("stockStatus") String stockStatus);
}
