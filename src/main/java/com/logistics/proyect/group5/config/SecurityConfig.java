package com.logistics.proyect.group5.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // La configuración de CORS vive en CorsConfig (bean CorsConfigurationSource).
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Autenticación pública
                        .requestMatchers(
                                "/v1/auth/**",
                                "/api/v1/auth/**",
                                "/auth/**"
                        ).permitAll()

                        // Endpoints administrativos
                        .requestMatchers(
                                "/admin/categories/**",
                                "/admin/products/**"
                        ).hasRole("ADMIN")

                        // Consulta pública del catálogo
                        .requestMatchers(
                                HttpMethod.GET,
                                "/products/**",
                                "/api/products/**",
                                "/categories/**",
                                "/api/categories/**"
                        ).permitAll()

                        // Paquetes y seguimiento
                        .requestMatchers(
                                "/packages/**",
                                "/tracking/**"
                        ).permitAll()

                        // Documentación y errores
                        .requestMatchers(
                                "/error",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Resto de endpoints requieren autenticación
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
