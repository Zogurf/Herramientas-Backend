package com.logistics.proyect.group5.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Única fuente de configuración de CORS. Spring Security la consume a través
 * del bean {@link CorsConfigurationSource} habilitado con
 * {@code http.cors(Customizer.withDefaults())}.
 *
 * <p>Los orígenes son configurables por entorno mediante la variable
 * {@code CORS_ALLOWED_ORIGINS} (lista separada por comas). Se admiten patrones,
 * por ejemplo {@code https://*.onrender.com} para los previews de Render.
 * Si no se define, se conservan los orígenes locales de desarrollo.
 */
@Configuration
@Slf4j
public class CorsConfig {

    private final List<String> allowedOriginPatterns;

    public CorsConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOriginPatterns = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (allowedOriginPatterns.isEmpty()) {
            log.warn("CORS: no hay orígenes permitidos configurados. "
                    + "Define app.cors.allowed-origins o la variable CORS_ALLOWED_ORIGINS; "
                    + "el navegador rechazará todas las peticiones del frontend.");
        } else {
            log.info("CORS habilitado para los orígenes: {}", allowedOriginPatterns);
        }

        CorsConfiguration configuration = new CorsConfiguration();
        // Se usan patrones (no orígenes literales) para admitir comodines y ser
        // compatible con allowCredentials(true).
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
