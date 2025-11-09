package com.lbg.markets.luxback.config;

import com.lbg.markets.luxback.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for development mode.
 * Uses HTTP Basic Authentication with in-memory users.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("dev-local")
@RequiredArgsConstructor
public class DevSecurityConfig {
    
    private final LuxBackConfig config;
    
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
                .httpBasic()
                .and()
                .formLogin()
                    .loginPage("/login")
                    .permitAll()
                .and()
                .logout()
                    .logoutSuccessUrl("/login?logout")
                    .permitAll();
        
        return http.build();
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username(config.getSecurity().getDevUsername())
                .password("{noop}" + config.getSecurity().getDevPassword())
                .roles(Role.USER.name())
                .build();
        
        UserDetails admin = User.builder()
                .username(config.getSecurity().getAdminUsername())
                .password("{noop}" + config.getSecurity().getAdminPassword())
                .roles(Role.ADMIN.name())
                .build();
        
        return new InMemoryUserDetailsManager(user, admin);
    }
}
