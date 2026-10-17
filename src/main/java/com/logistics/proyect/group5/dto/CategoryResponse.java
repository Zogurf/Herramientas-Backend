package com.logistics.proyect.group5.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryResponse {

    private Integer id;
    private String name;
    private String slug;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
