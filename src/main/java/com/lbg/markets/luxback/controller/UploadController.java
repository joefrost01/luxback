package com.lbg.markets.luxback.controller;

import com.lbg.markets.luxback.config.LuxBackConfig;
import com.lbg.markets.luxback.exception.FileSizeExceededException;
import com.lbg.markets.luxback.exception.InvalidFileTypeException;
import com.lbg.markets.luxback.model.FileMetadata;
import com.lbg.markets.luxback.security.SecurityUtils;
import com.lbg.markets.luxback.service.AuditService;
import com.lbg.markets.luxback.service.FileMetadataService;
import com.lbg.markets.luxback.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Controller for file upload operations.
 * Handles both the upload page view and file upload processing.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final StorageService storageService;
    private final AuditService auditService;
    private final FileMetadataService fileMetadataService;
    private final LuxBackConfig config;

    /**
     * Show the file upload page
     */
    @GetMapping({"/", "/upload"})
    public String uploadPage(Model model) {
        String username = SecurityUtils.getCurrentUsername();
        model.addAttribute("username", username);
        model.addAttribute("isAdmin", SecurityUtils.isAdmin());
        model.addAttribute("maxFileSize", config.getMaxFileSize());
        model.addAttribute("maxFileSizeMB", config.getMaxFileSize() / (1024 * 1024));
        return "upload";
    }

    /**
     * Handle file upload with streaming.
     * Files are never loaded entirely into memory.
     */
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<UploadResponse> handleFileUpload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {

        String username = SecurityUtils.getCurrentUsername();

        try {
            // Validate file
            fileMetadataService.validateFileSize(file.getSize());
            fileMetadataService.validateContentType(file.getContentType());

            // Generate storage filename
            String storedFilename = fileMetadataService.generateStorageFilename(file.getOriginalFilename());
            String path = config.getStoragePath() + "/" + username + "/" + storedFilename;

            // Stream file to storage - no memory buffering
            try (InputStream inputStream = file.getInputStream()) {
                storageService.writeFile(path, inputStream, file.getSize());
            }

            // Record audit event
            FileMetadata metadata = FileMetadata.builder()
                    .originalFilename(file.getOriginalFilename())
                    .storedFilename(storedFilename)
                    .size(file.getSize())
                    .contentType(file.getContentType())
                    .build();

            auditService.recordUpload(username, metadata, request);

            log.info("File uploaded successfully: user={}, file={}, size={}",
                    username, file.getOriginalFilename(), file.getSize());

            return ResponseEntity.ok(new UploadResponse("success",
                    "File uploaded successfully: " + file.getOriginalFilename()));

        } catch (FileSizeExceededException e) {
            log.warn("File too large: user={}, file={}, size={}",
                    username, file.getOriginalFilename(), file.getSize());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new UploadResponse("error", e.getMessage()));

        } catch (InvalidFileTypeException e) {
            log.warn("Invalid file type: user={}, file={}, type={}",
                    username, file.getOriginalFilename(), file.getContentType());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new UploadResponse("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Upload failed: user=" + username + ", file=" + file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new UploadResponse("error",
                            "Upload failed. Please try again or contact support."));
        }
    }

    /**
     * Response object for upload endpoint
     */
    @Data
    public static class UploadResponse {
        private final String status;
        private final String message;
    }
}