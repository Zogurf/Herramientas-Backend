package com.logistics.proyect.group5.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CategoryRequest {

    // Obligatorio al crear; CategoryService lo exige. En actualizaciones
    // parciales el nombre puede omitirse sin romper la validación del DTO.
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String name;

    @Size(max = 120, message = "El slug no puede superar 120 caracteres")
    private String slug;

    @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
    private String description;

    @Positive(message = "La categoría padre no es válida")
    private Integer parentId;

    /**
     * Permite distinguir "parentId omitido" de "parentId: null" (convertir la
     * categoría en raíz) en las actualizaciones parciales.
     */
    @Builder.Default
    private boolean parentIdProvided = false;

    @JsonSetter("parentId")
    public void setParentId(Integer parentId) {
        this.parentId = parentId;
        this.parentIdProvided = true;
    }

    @JsonAlias("isActive")
    private Boolean active;

    // Compatibilidad con llamadas Java de dev
    public Boolean getIsActive() {
        return active;
    }
}