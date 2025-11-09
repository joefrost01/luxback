package com.lbg.markets.luxback.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Custom error controller for user-friendly error pages
 */
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        String message = "An unexpected error occurred";
        String title = "Error";

        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());

            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                title = "Page Not Found";
                message = "The page you are looking for could not be found.";
            } else if (statusCode == HttpStatus.FORBIDDEN.value()) {
                title = "Access Denied";
                message = "You do not have permission to access this page.";
            } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                title = "Server Error";
                message = "An internal server error occurred. Please try again or contact support.";
            } else if (statusCode == HttpStatus.BAD_REQUEST.value()) {
                title = "Bad Request";
                message = "The request could not be understood by the server.";
            }
        }

        model.addAttribute("title", title);
        model.addAttribute("message", message);

        return "error";
    }
}