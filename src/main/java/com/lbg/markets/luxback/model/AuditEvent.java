package com.lbg.markets.luxback.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Represents a single audit event (upload or download).
 * Maps directly to CSV record structure.
 */
@Data
@Builder
public class AuditEvent {

    /**
     * Unique identifier for this event
     */
    private String eventId;

    /**
     * Type of event: UPLOAD or DOWNLOAD
     */
    private String eventType;

    /**
     * When the event occurred
     */
    private Instant timestamp;

    /**
     * Username of the file owner
     */
    private String username;

    /**
     * Original filename as uploaded
     */
    private String filename;

    /**
     * Storage filename (with timestamp prefix)
     */
    private String storedAs;

    /**
     * File size in bytes (null for downloads)
     */
    private Long fileSize;

    /**
     * MIME type (null for downloads)
     */
    private String contentType;

    /**
     * IP address of the client
     */
    private String ipAddress;

    /**
     * User agent string
     */
    private String userAgent;

    /**
     * HTTP session ID
     */
    private String sessionId;

    /**
     * Username of who performed the action (uploader or downloader)
     */
    private String actorUsername;
}
