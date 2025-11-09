package com.lbg.markets.luxback.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

/**
 * Utility class for accessing security context information
 */
public class SecurityUtils {
    
    /**
     * Get the username of the currently authenticated user
     * 
     * @return username or null if not authenticated
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
    
    /**
     * Check if current user has a specific role
     * 
     * @param role the role to check
     * @return true if user has the role
     */
    public static boolean hasRole(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String roleString = "ROLE_" + role.name();
        
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(roleString));
    }
    
    /**
     * Check if current user is an admin
     * 
     * @return true if user has ADMIN role
     */
    public static boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
