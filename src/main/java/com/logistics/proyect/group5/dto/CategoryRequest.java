package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 120)
    private String slug;

    private String description;

    private Integer parentId;

    private Boolean active;
}
