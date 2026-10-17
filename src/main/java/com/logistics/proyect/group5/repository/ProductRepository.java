package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySku(String sku);

    boolean existsByCategoryId(Integer categoryId);

    List<Product> findByActiveTrueOrderByCreatedAtDesc();

    List<Product> findAllByOrderByCreatedAtDesc();
}
