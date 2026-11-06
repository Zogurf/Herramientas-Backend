package com.logistics.proyect.group5.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FacebookAuthRequest {

    /** Access token de usuario emitido por el SDK de Facebook (FB.login). */
    @NotBlank(message = "El accessToken de Facebook es obligatorio.")
    private String accessToken;
}
