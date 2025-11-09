package com.lbg.markets.luxback.model;

import lombok.Builder;
import lombok.Data;

/**
 * Metadata about an uploaded file.
 * Used during upload processing and audit recording.
 */
@Data
@Builder
public class FileMetadata {
    
    /**
     * Original filename as provided by user
     */
    private String originalFilename;
    
    /**
     * Storage filename (timestamp-prefixed)
     */
    private String storedFilename;
    
    /**
     * File size in bytes
     */
    private long size;
    
    /**
     * MIME type
     */
    private String contentType;
}
