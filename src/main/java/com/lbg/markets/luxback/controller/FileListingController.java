package com.lbg.markets.luxback.controller;

import com.lbg.markets.luxback.model.AuditEvent;
import com.lbg.markets.luxback.model.SearchCriteria;
import com.lbg.markets.luxback.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for admin file listing and search.
 * Only accessible by users with ADMIN role.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class FileListingController {

    private final AuditService auditService;

    private static final int PAGE_SIZE = 10;

    /**
     * Display file listing page with search and pagination.
     * All parameters are optional - no filters means show all files.
     */
    @GetMapping("/files")
    @PreAuthorize("hasRole('ADMIN')")
    public String listFiles(
            @RequestParam(required = false) String filename,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        // Convert empty strings to null to avoid filtering on empty values
        filename = (filename != null && filename.isBlank()) ? null : filename;
        username = (username != null && username.isBlank()) ? null : username;

        // Build search criteria
        SearchCriteria criteria = SearchCriteria.builder()
                .filename(filename)
                .startDate(startDate)
                .endDate(endDate)
                .username(username)
                .build();

        // Search all audit events (fast in-memory search after initial cache load)
        List<AuditEvent> allResults = auditService.searchAllAudit(criteria);

        // Filter to only show UPLOAD events for file listing
        List<AuditEvent> uploadEvents = allResults.stream()
                .filter(event -> "UPLOAD".equals(event.getEventType()))
                .toList();

        // Paginate results
        int totalResults = uploadEvents.size();
        int totalPages = (totalResults + PAGE_SIZE - 1) / PAGE_SIZE;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalResults);

        List<AuditEvent> pageResults = (start < totalResults)
                ? uploadEvents.subList(start, end)
                : List.of();

        // Add attributes to model
        model.addAttribute("files", pageResults);
        model.addAttribute("totalResults", totalResults);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentPage", page);
        model.addAttribute("criteria", criteria);

        log.debug("File listing: page={}, results={}, total={}", page, pageResults.size(), totalResults);

        return "file-listing";
    }
}