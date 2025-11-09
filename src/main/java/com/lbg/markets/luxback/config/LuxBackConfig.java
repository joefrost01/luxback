package com.lbg.markets.luxback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Central configuration for LuxBack application.
 * Binds to 'luxback' prefix in application.yml.
 * <p>
 * This approach provides:
 * - Single injection point for configuration
 * - Type-safe access to properties
 * - IDE autocomplete support
 * - Clear visibility of all configuration options
 */
@Configuration
@ConfigurationProperties(prefix = "luxback")
@Data
public class LuxBackConfig {

    /**
     * Base path for file storage (local path or GCS bucket path like gs://bucket/backups)
     */
    private String storagePath;

    /**
     * Base path for audit CSV files
     */
    private String auditIndexPath;

    /**
     * Maximum file size in bytes (default 100MB)
     */
    private long maxFileSize = 104857600L;

    /**
     * List of allowed MIME types for uploads
     */
    private List<String> allowedContentTypes;

    /**
     * Security-related configuration
     */
    private Security security = new Security();

    @Data
    public static class Security {
        /**
         * Development mode username (basic auth)
         */
        private String devUsername;

        /**
         * Development mode password (basic auth)
         */
        private String devPassword;

        /**
         * Admin username for development mode
         */
        private String adminUsername;

        /**
         * Admin password for development mode
         */
        private String adminPassword;
    }
}
