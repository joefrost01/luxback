package com.lbg.markets.luxback.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Search criteria for filtering audit events.
 * All fields are optional - null means no filter on that field.
 */
@Data
@Builder
public class SearchCriteria {
    
    /**
     * Filter by filename (case-insensitive contains)
     */
    private String filename;
    
    /**
     * Filter by file owner username
     */
    private String username;
    
    /**
     * Filter events on or after this date
     */
    private LocalDate startDate;
    
    /**
     * Filter events on or before this date
     */
    private LocalDate endDate;
}
