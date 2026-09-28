package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.model.Product;
import com.logistics.proyect.group5.repository.ProductRepository;
import com.logistics.proyect.group5.service.CloudinaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final CloudinaryService cloudinaryService;
    private final ProductRepository productRepository;

    public ProductController(CloudinaryService cloudinaryService, ProductRepository productRepository) {
        this.cloudinaryService = cloudinaryService;
        this.productRepository = productRepository;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productRepository.findAll());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Product> uploadProduct(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "price", defaultValue = "0.0") BigDecimal price) {
        
        String imageUrl = cloudinaryService.uploadImage(file);

        Product product = Product.builder()
                .name(name)
                .price(price)
                .imageUrl(imageUrl)
                .build();

        Product savedProduct = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProduct);
    }
}
