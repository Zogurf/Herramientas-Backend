package com.logistics.proyect.group5.service;

import com.logistics.proyect.group5.config.JwtTokenProvider;
import com.logistics.proyect.group5.dto.*;
import com.logistics.proyect.group5.model.AuthProvider;
import com.logistics.proyect.group5.model.PasswordResetToken;
import com.logistics.proyect.group5.model.User;
import com.logistics.proyect.group5.model.UserRole;
import com.logistics.proyect.group5.repository.PasswordResetTokenRepository;
import com.logistics.proyect.group5.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final CloudinaryService cloudinaryService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = (User) authentication.getPrincipal();
        String token = tokenProvider.generateTokenFromUser(user);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(mapToUserSummaryDto(user))
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico ya se encuentra registrado.");
        }

        UserRole role = request.getRole() != null ? request.getRole() : UserRole.CLIENTE;

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        String token = tokenProvider.generateTokenFromUser(savedUser);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(mapToUserSummaryDto(savedUser))
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, String ipAddress) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail().trim().toLowerCase());
        if (userOptional.isEmpty()) {
            log.info("[AUTH SERVICE] Solicitud de recuperación para correo no registrado: {}", request.getEmail());
            return;
        }

        User user = userOptional.get();

        try {
            passwordResetTokenRepository.deleteByUser(user);
        } catch (Exception ignored) {}

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(token)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .ipAddress(ipAddress)
                .build();

        passwordResetTokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(request.getToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("El token de recuperación es inválido o no existe."));

        if (resetToken.isUsed()) {
            throw new IllegalArgumentException("Este enlace de recuperación ya ha sido utilizado.");
        }

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("El enlace de recuperación ha expirado. Por favor solicita uno nuevo.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        log.info("[AUTH SERVICE] Contraseña restablecida exitosamente para usuario: {}", user.getEmail());
    }

    @Transactional
    public UserSummaryDto updateProfile(User currentUser, UpdateProfileRequest request) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (request.getFullName() != null && !request.getFullName().trim().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }

        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        User updatedUser = userRepository.save(user);
        log.info("[AUTH SERVICE] Perfil actualizado para usuario: {}", user.getEmail());
        return mapToUserSummaryDto(updatedUser);
    }

    @Transactional
    public UserSummaryDto uploadAvatar(User currentUser, MultipartFile file) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        String avatarUrl = cloudinaryService.uploadImage(file);
        user.setAvatarUrl(avatarUrl);

        User updatedUser = userRepository.save(user);
        log.info("[AUTH SERVICE] Foto de perfil subida a Cloudinary ({}) para: {}", avatarUrl, user.getEmail());
        return mapToUserSummaryDto(updatedUser);
    }

    public UserSummaryDto mapToUserSummaryDto(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .provider(user.getProvider())
                .enabled(user.isEnabled())
                .build();
    }
}
