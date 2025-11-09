package com.lbg.markets.luxback.controller;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.exception.StorageException;
import com.lbg.markets.luxback.security.SecurityUtils;
import com.lbg.markets.luxback.service.AuditService;
import com.lbg.markets.luxback.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

/**
 * Controller for file download operations.
 * Only accessible by users with ADMIN role.
 * Downloads are streamed to avoid memory issues with large files.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DownloadController {

    private final StorageService storageService;
    private final AuditService auditService;
    private final LuxBackConfig config;

    /**
     * Download a file.
     * Files are streamed directly from storage without buffering.
     */
    @GetMapping("/download/{username}/{filename}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String username,
            @PathVariable String filename,
            HttpServletRequest request) {

        String downloaderUsername = SecurityUtils.getCurrentUsername();
        String path = config.getStoragePath() + "/" + username + "/" + filename;

        try {
            // Check if file exists
            if (!storageService.exists(path)) {
                log.warn("Download failed - file not found: path={}", path);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Get original filename from audit records for better user experience
            String originalFilename = auditService.getOriginalFilename(username, filename);

            // Create streaming response
            StreamingResponseBody stream = outputStream -> {
                try (InputStream inputStream = storageService.readFile(path)) {
                    inputStream.transferTo(outputStream);
                }
            };

            // Record download audit event
            auditService.recordDownload(username, originalFilename, filename,
                    downloaderUsername, request);

            log.info("File downloaded: owner={}, downloader={}, file={}",
                    username, downloaderUsername, originalFilename);

            // Return streaming response with appropriate headers
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + originalFilename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(stream);

        } catch (StorageException e) {
            log.error("Download failed: owner=" + username + ", file=" + filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}