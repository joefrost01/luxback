package com.lbg.markets.luxback.config;

import jakarta.servlet.MultipartConfigElement;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration for file uploads and static resources.
 * Configures multipart handling for large file uploads with streaming.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LuxBackConfig config;

    /**
     * Configure multipart file upload handling.
     * Sets appropriate limits for file size and enables streaming.
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        // Set max file size based on application config
        factory.setMaxFileSize(DataSize.ofBytes(config.getMaxFileSize()));

        // Set max request size slightly larger to account for form data
        factory.setMaxRequestSize(DataSize.ofBytes(config.getMaxFileSize() + 1024 * 1024));

        // Use system temp directory for temporary file storage during upload
        // Files are streamed, but this provides a fallback if needed
        factory.setLocation(System.getProperty("java.io.tmpdir"));

        return factory.createMultipartConfig();
    }

    /**
     * Configure static resource handlers for WebJars and custom resources
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // WebJars (Bootstrap, Dropzone) are automatically mapped by webjars-locator-core
        // but we can add custom mappings if needed
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
    }
}