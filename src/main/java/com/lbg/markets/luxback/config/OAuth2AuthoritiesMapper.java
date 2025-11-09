package com.lbg.markets.luxback.config;

import com.lbg.markets.luxback.security.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Maps Azure AD group membership to Spring Security roles.
 * Extracts the 'groups' claim from Azure AD tokens and maps group IDs to application roles.
 * <p>
 * Configuration required in application-{profile}.yml:
 * <pre>
 * azure:
 *   ad:
 *     groups:
 *       admin: "admin-group-guid-here"
 *       user: "user-group-guid-here"
 * </pre>
 */
@Component
@Profile({"int-gcp", "pre-prod-gcp", "prod-gcp"})
@Slf4j
public class OAuth2AuthoritiesMapper implements GrantedAuthoritiesMapper {

    private final String adminGroupId;
    private final String userGroupId;

    /**
     * Constructor that injects Azure AD group IDs from configuration.
     * These should be the Azure AD object IDs for the security groups.
     *
     * @param adminGroupId Azure AD group ID for admin users
     * @param userGroupId  Azure AD group ID for regular users
     */
    public OAuth2AuthoritiesMapper(
            @Value("${azure.ad.groups.admin:#{null}}") String adminGroupId,
            @Value("${azure.ad.groups.user:#{null}}") String userGroupId) {
        this.adminGroupId = adminGroupId;
        this.userGroupId = userGroupId;

        log.info("OAuth2 Authorities Mapper initialized");
        if (adminGroupId == null || userGroupId == null) {
            log.warn("Azure AD group IDs not fully configured. Role mapping may not work correctly.");
            log.warn("Please set azure.ad.groups.admin and azure.ad.groups.user in application.yml");
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

        authorities.forEach(authority -> {
            if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                // Extract user attributes from OIDC token
                Map<String, Object> attributes = oidcUserAuthority.getAttributes();

                // Log user info for debugging (without sensitive data)
                String email = (String) attributes.get("email");
                String name = (String) attributes.get("name");
                log.debug("Mapping authorities for user: name={}, email={}", name, email);

                // Extract groups claim - this contains Azure AD group memberships
                Object groupsClaim = attributes.get("groups");

                if (groupsClaim instanceof List<?> groupsList) {
                    Set<String> userGroups = new HashSet<>();
                    for (Object group : groupsList) {
                        if (group instanceof String groupId) {
                            userGroups.add(groupId);
                        }
                    }

                    log.debug("User groups: {}", userGroups);

                    // Map Azure AD groups to application roles
                    if (adminGroupId != null && userGroups.contains(adminGroupId)) {
                        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + Role.ADMIN.name()));
                        log.info("Granted ADMIN role to user: {}", email);
                    }

                    if (userGroupId != null && userGroups.contains(userGroupId)) {
                        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + Role.USER.name()));
                        log.info("Granted USER role to user: {}", email);
                    }

                    // Admins should also have USER role
                    if (mappedAuthorities.contains(new SimpleGrantedAuthority("ROLE_" + Role.ADMIN.name()))) {
                        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + Role.USER.name()));
                    }

                    if (mappedAuthorities.isEmpty()) {
                        log.warn("User {} has no matching group memberships. No roles assigned.", email);
                    }
                } else {
                    log.warn("No 'groups' claim found in token for user: {}. " +
                            "Ensure Azure AD app registration includes 'groups' in token claims.", email);
                }
            }
        });

        return mappedAuthorities;
    }
}
