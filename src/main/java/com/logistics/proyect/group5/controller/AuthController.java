package com.logistics.proyect.group5.controller;

import com.logistics.proyect.group5.dto.*;
import com.logistics.proyect.group5.model.User;
import com.logistics.proyect.group5.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody FacebookAuthRequest request) {
        AuthResponse response = authService.loginWithFacebook(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummaryDto> getCurrentUser(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserSummaryDto userSummary = authService.mapToUserSummaryDto(currentUser);
        return ResponseEntity.ok(userSummary);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        authService.forgotPassword(request, ipAddress);
        return ResponseEntity.ok(Map.of("message", "Se ha enviado un enlace de recuperación a tu correo electrónico."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida exitosamente. Ya puedes iniciar sesión."));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserSummaryDto> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @RequestBody UpdateProfileRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserSummaryDto updated = authService.updateProfile(currentUser, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserSummaryDto> uploadAvatar(
            @AuthenticationPrincipal User currentUser,
            @RequestParam("file") MultipartFile file) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserSummaryDto updated = authService.uploadAvatar(currentUser, file);
        return ResponseEntity.ok(updated);
    }
}
