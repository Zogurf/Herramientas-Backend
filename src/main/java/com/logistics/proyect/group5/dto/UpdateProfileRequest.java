package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String fullName;

    @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
    @Pattern(
            regexp = "^$|^\\+?[0-9][0-9\\s-]{5,19}$",
            message = "El teléfono debe tener entre 7 y 15 dígitos y solo puede incluir +, espacios o guiones"
    )
    private String phone;

    @Size(max = 255, message = "La URL del avatar no puede superar 255 caracteres")
    @Pattern(
            regexp = "^$|^https?://\\S+$",
            message = "La URL del avatar debe comenzar con http:// o https://"
    )
    private String avatarUrl;
}
