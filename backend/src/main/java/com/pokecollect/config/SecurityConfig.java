package com.pokecollect.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless OAuth2 resource server. The SPA authenticates directly with Supabase
 * (supabase-js) and sends the resulting ES256 JWT as a Bearer token; this app
 * only validates it against Supabase's JWKS (configured in application.yml).
 *
 * Public read endpoints stay open; collection + write endpoints require a token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public read-side endpoints
                .requestMatchers(HttpMethod.GET,
                    "/api/health",
                    "/api/search",
                    "/api/sets",
                    "/api/rare-cards",
                    "/api/market",
                    "/api/market/**",
                    "/card-image/**"
                ).permitAll()
                // Everything else (collection, user sync, future write commands) needs a JWT
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    // Allowed browser origins for the SPA: Vite dev (5173) and the nginx/Docker
    // origin (3000) by default; override with CORS_ALLOWED_ORIGINS (comma-separated)
    // to add a production domain. The browser sends an Origin header on POST even
    // same-origin, so the serving origin must be listed or Spring's CORS filter 403s it.
    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
