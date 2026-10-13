package com.powerdialer.controller;

import com.powerdialer.dto.LSCAuthFormDTO;
import com.powerdialer.service.LSCAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * /api/rest-client endpoint'i üzerinden istekleri alır.
 */
@Controller
@RequestMapping("/lscAuth")
public class LSCAuthTestController {

    @Autowired
    private LSCAuthService lscAuthService;

    @PostMapping(value = "/send", produces = "application/json")
    public ResponseEntity<?> sendRequest(@RequestBody LSCAuthFormDTO request) {
        try {
            Map<String, String> result = lscAuthService.sendRequest(request);
            return validateResult(result);

        } catch (Exception e) {
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * Validates the result map returned from the service and builds the appropriate HTTP response.
     */
    private ResponseEntity<?> validateResult(Map<String, String> result) {
        if (result == null) {
            return buildErrorResponse("Service returned null response");
        }

        String error = result.get("error");
        if (error != null) {
            return buildErrorResponse(error);
        }

        Object response = result.get("response");
        return ResponseEntity.ok(response != null ? response : "");
    }

    private ResponseEntity<String> buildErrorResponse(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message != null ? message : "Unknown error");
    }

}
