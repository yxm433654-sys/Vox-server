package com.vox.controller;

import com.vox.application.message.ClearConversationUseCase;
import com.vox.application.message.ListMessageHistoryUseCase;
import com.vox.application.message.MarkMessageReadUseCase;
import com.vox.application.message.PollMessagesUseCase;
import com.vox.application.message.SendMessageUseCase;
import com.vox.controller.common.ApiResponse;
import com.vox.controller.message.MessageHistoryResponse;
import com.vox.controller.message.MessageResponse;
import com.vox.controller.message.MessageResponseMapper;
import com.vox.controller.message.SendMessageRequest;
import com.vox.controller.message.SendMessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final ListMessageHistoryUseCase listMessageHistoryUseCase;
    private final PollMessagesUseCase pollMessagesUseCase;
    private final SendMessageUseCase sendMessageUseCase;
    private final MarkMessageReadUseCase markMessageReadUseCase;
    private final ClearConversationUseCase clearConversationUseCase;
    private final MessageResponseMapper messageResponseMapper;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<SendMessageResponse>> send(@Valid @RequestBody SendMessageRequest request) {
        try {
            SendMessageResponse response = messageResponseMapper.toSendResponse(
                    sendMessageUseCase.execute(messageResponseMapper.toCommand(request))
            );
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
    public ResponseEntity<ApiResponse<List<MessageResponse>>> poll(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "lastMessageId", required = false) Long lastMessageId
    ) {
        try {
            List<MessageResponse> messages = messageResponseMapper.toResponses(
                    pollMessagesUseCase.execute(userId, lastMessageId)
            );
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
            @RequestParam(value = "peerId", required = false) Long peerId,
            @RequestParam(value = "sessionId", required = false) Long sessionId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        try {
            MessageHistoryResponse response = messageResponseMapper.toHistoryResponse(
                    sessionId != null
                            ? listMessageHistoryUseCase.bySession(userId, sessionId, page, size)
                            : listMessageHistoryUseCase.byPeer(userId, peerId, page, size)
            );
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
            markMessageReadUseCase.execute(id);
            return ResponseEntity.ok(ApiResponse.success(null, "Message marked as read"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Mark message read failed: id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Mark read failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/conversation")
    public ResponseEntity<ApiResponse<Void>> clearConversation(
            @RequestParam("userId") Long userId,
            @RequestParam("peerId") Long peerId
    ) {
        try {
            int cleared = clearConversationUseCase.execute(userId, peerId);
            return ResponseEntity.ok(ApiResponse.success(null, "Cleared " + cleared + " messages"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Clear conversation failed: userId={}, peerId={}", userId, peerId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Clear conversation failed: " + e.getMessage()));
        }
    }
}
