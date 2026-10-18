package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @Size(max = 120, message = "El slug no puede superar 120 caracteres")
    private String slug;

    private String description;

    private Integer parentId;

    @JsonAlias("isActive")
    private Boolean active;

    // Compatibilidad con llamadas Java de dev
    public Boolean getIsActive() {
        return active;
    }
}