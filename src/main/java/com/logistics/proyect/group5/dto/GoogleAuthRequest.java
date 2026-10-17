package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthRequest {

    @NotBlank(message = "El credential de Google es obligatorio.")
    private String credential;
}
