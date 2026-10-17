package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {

    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @Size(max = 120, message = "El slug no puede superar 120 caracteres")
    private String slug;

    private String description;

    private Boolean isActive;
}
