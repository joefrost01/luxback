package com.lbg.markets.luxback.config;

import com.lbg.markets.luxback.security.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for production environments (GCP).
 * Uses Azure AD OAuth2 for authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
public class ProdSecurityConfig {
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/webjars/**", "/css/**", "/js/**", "/actuator/health").permitAll()
                        // User endpoints - accessible by USER and ADMIN
                        .requestMatchers("/", "/upload").hasAnyRole(Role.USER.name(), Role.ADMIN.name())
                        // Admin-only endpoints
                        .requestMatchers("/files/**", "/download/**").hasRole(Role.ADMIN.name())
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .oauth2Login()
                .and()
                .oauth2Client()
                .and()
                .logout()
                    .logoutSuccessUrl("/")
                    .permitAll();
        
        return http.build();
    }
}
