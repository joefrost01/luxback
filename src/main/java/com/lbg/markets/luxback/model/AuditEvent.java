package com.lbg.markets.luxback.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditEvent {

    String eventId;
    String eventType;
    String username;
    long fileSize;
    String contentType;
    String ipAddress;
    String userAgent;
    String sessionId;
    String actorUsername;
    LocalDateTime timestamp;
    String storedAs;
    String filename;

}
