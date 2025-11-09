package com.lbg.markets.luxback.config;

import com.lbg.markets.luxback.security.CustomAccessDeniedHandler;
import com.lbg.markets.luxback.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for production environments (GCP).
 * Uses Azure AD OAuth2 for authentication with role mapping.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
@RequiredArgsConstructor
public class ProdSecurityConfig {

    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final GrantedAuthoritiesMapper authoritiesMapper;

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
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(authoritiesMapper)
                        )
                )
                .oauth2Client()
                .and()
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }
}
