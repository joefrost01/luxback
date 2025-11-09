package com.lbg.markets.luxback.service;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.model.AuditEvent;
import com.lbg.markets.luxback.model.FileMetadata;
import com.lbg.markets.luxback.model.SearchCriteria;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Service for managing audit logs using CSV files.
 * Features:
 * - Per-user CSV files for natural partitioning
 * - Per-user caching for performance
 * - Per-user locking to prevent write conflicts
 * - Append-only operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private static final String[] CSV_HEADERS = {
            "event_id", "event_type", "timestamp", "username", "filename",
            "stored_as", "file_size", "content_type", "ip_address",
            "user_agent", "session_id", "actor_username"
    };

    private final StorageService storage;
    private final LuxBackConfig config;

    // Per-user locks to prevent concurrent writes to same user's CSV
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    // Per-user cache of audit events
    private final ConcurrentHashMap<String, List<AuditEvent>> perUserCache = new ConcurrentHashMap<>();

    /**
     * Record a file upload event
     */
    public void recordUpload(String username, FileMetadata metadata, HttpServletRequest request) {
        ReentrantLock lock = userLocks.computeIfAbsent(username, k -> new ReentrantLock());
        lock.lock();
        try {
            appendAuditEvent(username,
                    UUID.randomUUID().toString(),
                    "UPLOAD",
                    Instant.now(),
                    username,
                    metadata.getOriginalFilename(),
                    metadata.getStoredFilename(),
                    metadata.getSize(),
                    metadata.getContentType(),
                    getClientIp(request),
                    request.getHeader("User-Agent"),
                    request.getSession().getId(),
                    username // actor is uploader
            );

            // Invalidate cache for this user
            perUserCache.remove(username);

            log.info("Recorded upload: user={}, file={}", username, metadata.getOriginalFilename());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Record a file download event
     */
    public void recordDownload(String fileOwner, String originalFilename, String storedFilename,
                               String downloaderUsername, HttpServletRequest request) {
        ReentrantLock lock = userLocks.computeIfAbsent(fileOwner, k -> new ReentrantLock());
        lock.lock();
        try {
            appendAuditEvent(fileOwner,
                    UUID.randomUUID().toString(),
                    "DOWNLOAD",
                    Instant.now(),
                    fileOwner,
                    originalFilename,
                    storedFilename,
                    null, // file_size not needed for download
                    null, // content_type not needed for download
                    getClientIp(request),
                    request.getHeader("User-Agent"),
                    request.getSession().getId(),
                    downloaderUsername // actor is downloader
            );

            // Invalidate cache for file owner
            perUserCache.remove(fileOwner);

            log.info("Recorded download: owner={}, downloader={}, file={}",
                    fileOwner, downloaderUsername, originalFilename);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Search all audit events across all users
     */
    public List<AuditEvent> searchAllAudit(SearchCriteria criteria) {
        List<AuditEvent> allEvents = getAllUsernames().stream()
                .flatMap(username -> getUserEvents(username).stream())
                .toList();

        return allEvents.stream()
                .filter(event -> matchesCriteria(event, criteria))
                .sorted(Comparator.comparing(AuditEvent::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get the original filename for a stored file from audit records
     */
    public String getOriginalFilename(String username, String storedFilename) {
        return getUserEvents(username).stream()
                .filter(event -> storedFilename.equals(event.getStoredAs()))
                .findFirst()
                .map(AuditEvent::getFilename)
                .orElse(storedFilename); // Fallback to stored filename
    }

    /**
     * Get audit events for a specific user (with caching)
     */
    private List<AuditEvent> getUserEvents(String username) {
        return perUserCache.computeIfAbsent(username, this::loadUserAudit);
    }

    /**
     * Load audit events from user's CSV file
     */
    private List<AuditEvent> loadUserAudit(String username) {
        String path = config.getAuditIndexPath() + "/" + username + ".csv";

        try {
            if (!storage.exists(path)) {
                log.debug("No audit file for user: {}", username);
                return new ArrayList<>();
            }

            String csv = storage.readString(path);
            CSVParser parser = CSVParser.parse(csv, CSVFormat.DEFAULT.withFirstRecordAsHeader());

            List<AuditEvent> events = parser.stream()
                    .map(this::recordToAuditEvent)
                    .collect(Collectors.toList());

            log.debug("Loaded {} audit events for user: {}", events.size(), username);
            return events;

        } catch (IOException e) {
            log.error("Failed to read audit file for user: " + username, e);
            return new ArrayList<>();
        }
    }

    /**
     * Append an audit event to user's CSV file
     */
    private void appendAuditEvent(String username, Object... values) {
        String path = config.getAuditIndexPath() + "/" + username + ".csv";
        boolean fileExists = storage.exists(path);

        try {
            StringWriter sw = new StringWriter();
            CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);

            // Write header if new file
            if (!fileExists) {
                printer.printRecord((Object[]) CSV_HEADERS);
            }

            // Write the event record
            printer.printRecord(values);
            printer.close();

            // Append to file or create new file
            if (fileExists) {
                storage.append(path, sw.toString());
            } else {
                storage.writeString(path, sw.toString());
            }

        } catch (IOException e) {
            log.error("Failed to append audit event for user: " + username, e);
            throw new RuntimeException("Failed to record audit event", e);
        }
    }

    /**
     * Convert CSV record to AuditEvent object
     */
    private AuditEvent recordToAuditEvent(CSVRecord record) {
        return AuditEvent.builder()
                .eventId(record.get("event_id"))
                .eventType(record.get("event_type"))
                .timestamp(LocalDateTime.parse(record.get("timestamp")))
                .username(record.get("username"))
                .filename(record.get("filename"))
                .storedAs(record.get("stored_as"))
                .fileSize(parseOptionalLong(record, "file_size"))
                .contentType(getOptional(record, "content_type"))
                .ipAddress(record.get("ip_address"))
                .userAgent(getOptional(record, "user_agent"))
                .sessionId(getOptional(record, "session_id"))
                .actorUsername(record.get("actor_username"))
                .build();
    }

    /**
     * Get optional string value from CSV record
     */
    private String getOptional(CSVRecord record, String column) {
        try {
            String value = record.isMapped(column) ? record.get(column) : null;
            return (value == null || value.isBlank()) ? null : value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parse optional long value from CSV record
     */
    private Long parseOptionalLong(CSVRecord record, String column) {
        String value = getOptional(record, column);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Check if audit event matches search criteria
     */
    private boolean matchesCriteria(AuditEvent event, SearchCriteria criteria) {
        if (criteria == null) {
            return true;
        }

        if (criteria.getFilename() != null &&
                !event.getFilename().toLowerCase().contains(criteria.getFilename().toLowerCase())) {
            return false;
        }

        if (criteria.getUsername() != null &&
                !event.getUsername().equals(criteria.getUsername())) {
            return false;
        }

        if (criteria.getStartDate() != null) {
            LocalDate eventDate = event.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
            if (eventDate.isBefore(criteria.getStartDate())) {
                return false;
            }
        }

        if (criteria.getEndDate() != null) {
            LocalDate eventDate = event.getTimestamp().atZone(ZoneId.systemDefault()).toLocalDate();
            return !eventDate.isAfter(criteria.getEndDate());
        }

        return true;
    }

    /**
     * Extract client IP address from request
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * Get all usernames from audit index files
     */
    private List<String> getAllUsernames() {
        return storage.listFiles(config.getAuditIndexPath()).stream()
                .filter(path -> path.endsWith(".csv"))
                .map(this::extractUsernameFromPath)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Extract username from audit file path
     */
    private String extractUsernameFromPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');

        if (lastSlash >= 0 && lastDot > lastSlash) {
            return path.substring(lastSlash + 1, lastDot);
        }
        return null;
    }
}