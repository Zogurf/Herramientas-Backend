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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
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

    @Value("${google.client-id:}")
    private String googleClientId;

    /** App ID de Meta (nombrado "client-id" por coherencia con Google). */
    @Value("${facebook.client-id:}")
    private String facebookClientId;

    /** App Secret de Meta; se usa para verificar que el token es de esta app. */
    @Value("${facebook.client-secret:}")
    private String facebookClientSecret;

    @Value("${facebook.api-version:v26.0}")
    private String facebookApiVersion;

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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El correo electrónico ya se encuentra registrado.");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(UserRole.CLIENTE)
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        // saveAndFlush garantiza que la fila exista antes de asignar el rol (FK de user_roles).
        User savedUser = userRepository.saveAndFlush(user);
        ensureDefaultRole(savedUser, UserRole.CLIENTE);

        String token = tokenProvider.generateTokenFromUser(savedUser);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(mapToUserSummaryDto(savedUser))
                .build();
    }

    /**
     * Persiste el rol por defecto en {@code auth_schema.user_roles}, que es la
     * única fuente de verdad del RBAC, y sincroniza el rol transitorio de la
     * entidad para que el JWT emitido refleje el estado persistido.
     * <p>
     * Solo actúa si el usuario no tiene ningún rol, por lo que es seguro
     * invocarlo también en logins posteriores (repara cuentas antiguas).
     */
    private void ensureDefaultRole(User user, UserRole role) {
        if (user.getId() == null) {
            return;
        }
        if (userRepository.assignDefaultRoleIfNone(user.getId(), role.name()) > 0) {
            user.setRole(role);
            log.info("[AUTH SERVICE] Rol {} asignado al usuario {}", role, user.getEmail());
        }
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

    // =====================================================================
    // OAuth2 / Social Login (Google y Facebook)
    // =====================================================================

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        Map<String, Object> googleUser = verifyGoogleToken(request.getCredential());

        String email = (String) googleUser.get("email");
        String name = (String) googleUser.get("name");
        String picture = (String) googleUser.get("picture");
        String googleId = (String) googleUser.get("sub");

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("No se pudo obtener el correo electrónico de la cuenta de Google.");
        }

        return loginWithSocialUser(AuthProvider.GOOGLE, googleId, email, name, picture, "Google");
    }

    @Transactional
    public AuthResponse loginWithFacebook(FacebookAuthRequest request) {
        Map<String, Object> facebookUser = verifyFacebookToken(request.getAccessToken());

        String facebookId = (String) facebookUser.get("id");
        String email = (String) facebookUser.get("email");
        String name = (String) facebookUser.get("name");
        String picture = extractFacebookPicture(facebookUser);

        if (email == null || email.isBlank()) {
            // Facebook solo entrega el correo si el usuario lo concedió y lo tiene
            // verificado. Sin correo no hay forma de identificar la cuenta.
            throw new IllegalArgumentException(
                    "No se pudo obtener el correo electrónico de la cuenta de Facebook. "
                    + "Autoriza el permiso de correo al iniciar sesión e inténtalo de nuevo.");
        }

        return loginWithSocialUser(AuthProvider.FACEBOOK, facebookId, email, name, picture, "Facebook");
    }

    /**
     * Crea o vincula la cuenta local a partir de un proveedor social ya verificado.
     * <p>
     * Las reglas son idénticas para Google y Facebook: si el correo ya existe se
     * reutiliza la cuenta (rellenando el avatar solo si estaba vacío y migrando el
     * proveedor si era {@code LOCAL}, es decir, un registro por contraseña); si no
     * existe, se crea con rol {@code CLIENTE} y una contraseña aleatoria, ya que la
     * autenticación real la aporta el proveedor.
     */
    private AuthResponse loginWithSocialUser(AuthProvider provider, String providerId, String email,
                                             String name, String picture, String providerLabel) {
        Optional<User> existingUser = userRepository.findByEmail(email);
        User user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            // Rellena el avatar solo si el usuario aún no tiene uno propio.
            if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && picture != null) {
                user.setAvatarUrl(picture);
            }
            // Migra el proveedor si la cuenta se había creado con correo y contraseña.
            if (user.getProvider() == AuthProvider.LOCAL) {
                user.setProvider(provider);
                user.setProviderId(providerId);
            }
            user = userRepository.save(user);
            ensureDefaultRole(user, UserRole.CLIENTE); // repara cuentas creadas antes de persistir el rol
            log.info("[AUTH SERVICE] Login con {} exitoso (usuario existente): {}", providerLabel, email);
        } else {
            user = User.builder()
                    .fullName(name != null && !name.isBlank() ? name : email.split("@")[0])
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .avatarUrl(picture)
                    .provider(provider)
                    .providerId(providerId)
                    .role(UserRole.CLIENTE)
                    .enabled(true)
                    .build();
            user = userRepository.saveAndFlush(user);
            ensureDefaultRole(user, UserRole.CLIENTE);
            log.info("[AUTH SERVICE] Nuevo usuario registrado con {}: {}", providerLabel, email);
        }

        String token = tokenProvider.generateTokenFromUser(user);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(mapToUserSummaryDto(user))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyGoogleToken(String idToken) {
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;

            Map<String, Object> response = restTemplate().getForObject(url, Map.class);

            if (response == null) {
                throw new IllegalArgumentException("Respuesta vacía de Google.");
            }

            // Verify the token was issued for our app
            String audience = (String) response.get("aud");
            if (googleClientId != null && !googleClientId.isBlank() && !googleClientId.equals(audience)) {
                log.warn("[AUTH SERVICE] Google token audience mismatch. Expected: {}, Got: {}", googleClientId, audience);
                throw new IllegalArgumentException("El token de Google no corresponde a esta aplicación.");
            }

            return response;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[AUTH SERVICE] Error al verificar token de Google: {}", ex.getMessage());
            throw new IllegalArgumentException("Token de Google inválido o expirado.");
        }
    }

    /**
     * Verifica un access token de Facebook contra la Graph API y devuelve el perfil.
     * <p>
     * El paso crítico es {@code debug_token}: no basta con que el token sea válido,
     * hay que comprobar que fue emitido para <b>esta</b> aplicación comparando
     * {@code app_id}. Sin esa comprobación, un token legítimo de otra app de
     * Facebook sería aceptado y cualquiera podría suplantar a un usuario.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyFacebookToken(String accessToken) {
        if (facebookClientId == null || facebookClientId.isBlank()
                || facebookClientSecret == null || facebookClientSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "El inicio de sesión con Facebook no está configurado en el servidor.");
        }

        // debug_token se autentica con un "app access token": app_id|app_secret.
        String appAccessToken = facebookClientId + "|" + facebookClientSecret;

        try {
            String debugUrl = "https://graph.facebook.com/" + facebookApiVersion
                    + "/debug_token?input_token=" + encode(accessToken)
                    + "&access_token=" + encode(appAccessToken);

            Map<String, Object> debugResponse = restTemplate().getForObject(debugUrl, Map.class);

            Object dataObject = debugResponse == null ? null : debugResponse.get("data");
            if (!(dataObject instanceof Map)) {
                throw new IllegalArgumentException("Token de Facebook inválido o expirado.");
            }

            Map<String, Object> data = (Map<String, Object>) dataObject;
            if (!Boolean.TRUE.equals(data.get("is_valid"))) {
                throw new IllegalArgumentException("Token de Facebook inválido o expirado.");
            }
            if (!facebookClientId.equals(data.get("app_id"))) {
                log.warn("[AUTH SERVICE] Facebook token app_id mismatch. Expected: {}, Got: {}",
                        facebookClientId, data.get("app_id"));
                throw new IllegalArgumentException("El token de Facebook no corresponde a esta aplicación.");
            }

            String profileUrl = "https://graph.facebook.com/" + facebookApiVersion
                    + "/me?fields=id,name,email,picture.type(large)&access_token=" + encode(accessToken);

            Map<String, Object> profile = restTemplate().getForObject(profileUrl, Map.class);
            if (profile == null) {
                throw new IllegalArgumentException("Respuesta vacía de Facebook.");
            }
            return profile;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[AUTH SERVICE] Error al verificar token de Facebook: {}", ex.getMessage());
            throw new IllegalArgumentException("Token de Facebook inválido o expirado.");
        }
    }

    /**
     * Extrae la URL del avatar del formato anidado de la Graph API
     * ({@code picture.data.url}) con tolerancia a respuestas incompletas.
     */
    @SuppressWarnings("unchecked")
    private String extractFacebookPicture(Map<String, Object> profile) {
        Object picture = profile.get("picture");
        if (!(picture instanceof Map)) {
            return null;
        }
        Object data = ((Map<String, Object>) picture).get("data");
        if (!(data instanceof Map)) {
            return null;
        }
        Object url = ((Map<String, Object>) data).get("url");
        return url instanceof String ? (String) url : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Cliente HTTP con tiempos de espera acotados. Por defecto {@link RestTemplate}
     * no los aplica y una llamada al proveedor que se quede colgada bloquearía el
     * hilo de la petición de login indefinidamente.
     */
    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return new RestTemplate(factory);
    }
}
