package com.vox.controller;

import com.chatapp.dto.ApiResponse;
import com.vox.application.session.ListSessionsUseCase;
import com.vox.controller.session.SessionResponse;
import com.vox.controller.session.SessionResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final ListSessionsUseCase listSessionsUseCase;
    private final SessionResponseMapper sessionResponseMapper;

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> list(@RequestParam("userId") Long userId) {
        try {
            List<SessionResponse> responses = listSessionsUseCase.execute(userId).stream()
                    .map(sessionResponseMapper::toResponse)
                    .toList();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("List sessions failed: userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("List sessions failed: " + e.getMessage()));
        }
    }
}
