package com.lbg.markets.luxback.config;

import com.lbg.markets.luxback.security.CustomAccessDeniedHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Test configuration that provides properly initialized beans for tests.
 * Used in @WebMvcTest tests where @ConfigurationProperties binding doesn't work.
 */
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public LuxBackConfig testLuxBackConfig() {
        LuxBackConfig config = new LuxBackConfig();

        // Initialize Security
        LuxBackConfig.Security security = new LuxBackConfig.Security();
        security.setDevUsername("user");
        security.setDevPassword("password");
        security.setAdminUsername("admin");
        security.setAdminPassword("adminpass");
        config.setSecurity(security);

        // Initialize other properties
        config.setStoragePath("test_data/backups");
        config.setAuditIndexPath("test_data/audit-indexes");
        config.setMaxFileSize(104857600L); // 100MB
        config.setAllowedContentTypes(List.of(
                "application/pdf",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain",
                "text/csv",
                "image/png",
                "image/jpeg"
        ));

        return config;
    }

    /**
     * Provide CustomAccessDeniedHandler bean for tests.
     * This is required by DevSecurityConfig but not automatically available in @WebMvcTest.
     */
    @Bean
    public CustomAccessDeniedHandler customAccessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }
}
