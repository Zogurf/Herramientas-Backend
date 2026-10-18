package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import com.logistics.proyect.group5.dto.CategoryRequest;
import com.logistics.proyect.group5.dto.CategoryResponse;
import com.logistics.proyect.group5.exception.ResourceNotFoundException;
import com.logistics.proyect.group5.repository.ProductRepository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Category> findByActiveTrue();

    List<Category> findByNameContainingIgnoreCase(String name);

    boolean existsByParent_Id(Integer parentId);

    List<Category> findAllByOrderByNameAsc();
}