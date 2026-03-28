package com.chatapp.controller;

import com.chatapp.dto.*;
import com.chatapp.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<MessageSendResponse>> send(@Valid @RequestBody MessageSendRequest request) {
        try {
            MessageSendResponse response = messageService.send(request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Send message failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Send failed: " + e.getMessage()));
        }
    }

    @GetMapping("/poll")
    public ResponseEntity<ApiResponse<List<MessageDto>>> poll(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "lastMessageId", required = false) Long lastMessageId
    ) {
        try {
            List<MessageDto> messages = messageService.poll(userId, lastMessageId);
            return ResponseEntity.ok(ApiResponse.success(messages));
        } catch (Exception e) {
            log.error("Poll messages failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Poll failed: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<MessageHistoryResponse>> history(
            @RequestParam("userId") Long userId,
            @RequestParam("peerId") Long peerId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        try {
            MessageHistoryResponse response = messageService.history(userId, peerId, page, size);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Get message history failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("History failed: " + e.getMessage()));
        }
    }

    @PutMapping("/read/{id}")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable("id") Long id) {
        try {
            messageService.markRead(id);
            return ResponseEntity.ok(ApiResponse.success(null, "Message marked as read"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Mark message read failed: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Mark read failed: " + e.getMessage()));
        }
    }
}
