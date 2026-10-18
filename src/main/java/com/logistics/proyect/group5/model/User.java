package com.logistics.proyect.group5.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "auth_schema")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String password;

    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * Role resolved via RBAC tables (auth_schema.user_roles → auth_schema.roles).
     * Defaults to CLIENTE when no role row exists.
     * <p>
     * Si el usuario tiene varias filas de rol, se resuelve de forma determinista
     * al de mayor privilegio (ADMIN &gt; REPARTIDOR &gt; resto). Con {@code LIMIT 1}
     * sin {@code ORDER BY} el resultado dependía del plan de ejecución.
     */
    @Formula("(SELECT COALESCE(r.name, 'CLIENTE') FROM auth_schema.user_roles ur " +
             "LEFT JOIN auth_schema.roles r ON ur.role_id = r.id " +
             "WHERE ur.user_id = id " +
             "ORDER BY CASE r.name " +
             "  WHEN 'ADMIN' THEN 0 " +
             "  WHEN 'REPARTIDOR' THEN 1 " +
             "  ELSE 2 END " +
             "LIMIT 1)")
    private String roleName;

    /** Transient enum view — populated from roleName after load */
    @Transient
    @Builder.Default
    private UserRole role = UserRole.CLIENTE;

    @Column(name = "provider")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PostLoad
    protected void onLoad() {
        if (roleName != null && !roleName.isBlank()) {
            // Tolerante al prefijo 'ROLE_' de bases sin migrar: el enum no lo incluye.
            String normalized = roleName.trim().toUpperCase();
            if (normalized.startsWith("ROLE_")) {
                normalized = normalized.substring("ROLE_".length());
            }
            try {
                this.role = UserRole.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                this.role = UserRole.CLIENTE;
            }
        }
        if (this.provider == null) {
            this.provider = AuthProvider.LOCAL;
        }
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
