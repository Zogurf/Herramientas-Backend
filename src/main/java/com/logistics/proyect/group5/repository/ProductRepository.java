package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
