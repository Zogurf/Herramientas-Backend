package com.logistics.proyect.group5.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private Integer id;
    private String name;
    private String slug;
    private String description;
    private Integer parentId;
    private boolean active;
    private LocalDateTime createdAt;
}
