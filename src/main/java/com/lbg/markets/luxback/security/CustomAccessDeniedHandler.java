package com.lbg.markets.luxback.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom handler for access denied (403 Forbidden) errors.
 * Logs the violation and returns proper 403 status with error page.
 */
@Component
@Slf4j
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        String username = request.getUserPrincipal() != null
                ? request.getUserPrincipal().getName()
                : "anonymous";

        log.warn("Access denied: user={}, path={}, method={}",
                username, request.getRequestURI(), request.getMethod());

        // Set the response status to 403 BEFORE forwarding
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("javax.servlet.error.status_code", HttpServletResponse.SC_FORBIDDEN);

        // Forward to error page
        request.getRequestDispatcher("/error").forward(request, response);
    }
}
