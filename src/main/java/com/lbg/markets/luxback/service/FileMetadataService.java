package com.lbg.markets.luxback.service;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.exception.FileSizeExceededException;
import com.lbg.markets.luxback.exception.InvalidFileTypeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Service for file metadata operations including naming and validation
 */
@Service
@RequiredArgsConstructor
public class FileMetadataService {
    
    private final LuxBackConfig config;
    
    private static final DateTimeFormatter FILENAME_TIMESTAMP_FORMAT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
                    .withZone(ZoneId.systemDefault());
    
    /**
     * Generate a storage filename with timestamp prefix
     * Format: 2024-11-09T14-30-00_original-filename.ext
     * 
     * @param originalFilename the original filename
     * @return timestamped storage filename
     */
    public String generateStorageFilename(String originalFilename) {
        String sanitized = sanitizeFilename(originalFilename);
        String timestamp = FILENAME_TIMESTAMP_FORMAT.format(Instant.now());
        return timestamp + "_" + sanitized;
    }
    
    /**
     * Sanitize filename to remove potentially dangerous characters
     * 
     * @param filename the filename to sanitize
     * @return sanitized filename
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed_file";
        }
        
        // Remove path traversal attempts
        String sanitized = filename.replaceAll("[./\\\\]", "_");
        
        // Remove any other potentially problematic characters
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // Ensure it doesn't start with a dot (hidden file)
        if (sanitized.startsWith("_")) {
            sanitized = "file" + sanitized;
        }
        
        return sanitized;
    }
    
    /**
     * Validate file size against configured maximum
     * 
     * @param size the file size in bytes
     * @throws FileSizeExceededException if file is too large
     */
    public void validateFileSize(long size) {
        if (size > config.getMaxFileSize()) {
            long maxMB = config.getMaxFileSize() / (1024 * 1024);
            throw new FileSizeExceededException(
                    String.format("File exceeds maximum size of %d MB", maxMB));
        }
    }
    
    /**
     * Validate file content type against whitelist
     * 
     * @param contentType the MIME type
     * @throws InvalidFileTypeException if type not allowed
     */
    public void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidFileTypeException("File type could not be determined");
        }
        
        if (config.getAllowedContentTypes() == null || config.getAllowedContentTypes().isEmpty()) {
            // No restrictions if not configured
            return;
        }
        
        boolean allowed = config.getAllowedContentTypes().stream()
                .anyMatch(allowedType -> contentType.toLowerCase().startsWith(allowedType.toLowerCase()));
        
        if (!allowed) {
            throw new InvalidFileTypeException(
                    String.format("File type '%s' is not allowed. Supported types: %s",
                            contentType, String.join(", ", config.getAllowedContentTypes())));
        }
    }
}
