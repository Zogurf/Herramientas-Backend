package com.logistics.proyect.group5.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Cadena exclusiva de Actuator. Se evalúa antes que la cadena JWT y no
     * incluye su filtro: aquí la autenticación es HTTP Basic con rol MONITOR,
     * no un Bearer token.
     *
     * <p>Quedan públicas solo las sondas que necesita el despliegue
     * (/actuator/health e /actuator/info); cualquier otro endpoint de Actuator
     * exige credenciales.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Value("${monitoring.user:}") String user,
            @Value("${monitoring.password:}") String password) throws Exception {

        // Usuario de métricas desde MANAGEMENT_USER / MANAGEMENT_PASSWORD.
        // Si falta cualquiera de las dos, el manager queda sin usuarios: los
        // endpoints protegidos responden 401 y nunca se usan credenciales por
        // defecto (falla cerrado).
        InMemoryUserDetailsManager metricsUsers = new InMemoryUserDetailsManager();
        if (StringUtils.hasText(user) && StringUtils.hasText(password)) {
            metricsUsers.createUser(
                    User.withUsername(user)
                            .password(passwordEncoder.encode(password))
                            .roles("MONITOR")
                            .build()
            );
        }

        DaoAuthenticationProvider metricsAuthProvider = new DaoAuthenticationProvider(metricsUsers);
        metricsAuthProvider.setPasswordEncoder(passwordEncoder);

        http
                .securityMatcher("/actuator/**")
                // El proveedor se registra solo en el AuthenticationManager de
                // esta cadena: el usuario de métricas no existe para el resto de
                // la aplicación ni interfiere con los usuarios de la base.
                .authenticationProvider(metricsAuthProvider)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info"
                        ).permitAll()
                        .anyRequest().hasRole("MONITOR")
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

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
