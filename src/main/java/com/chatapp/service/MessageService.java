package com.chatapp.service;

import com.chatapp.dto.MessageDto;
import com.chatapp.dto.MessageHistoryResponse;
import com.chatapp.dto.MessageSendRequest;
import com.chatapp.dto.MessageSendResponse;
import com.chatapp.dto.Pagination;
import com.chatapp.entity.FileResource;
import com.chatapp.entity.Message;
import com.chatapp.repository.FileResourceRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.websocket.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final FileResourceRepository fileResourceRepository;
    private final StorageUrlService storageUrlService;
    private final WebSocketPushService webSocketPushService;

    @Transactional
    public MessageSendResponse send(MessageSendRequest request) {
        Message.MessageType type;
        try {
            type = Message.MessageType.valueOf(request.getType());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid message type");
        }

        if (type == Message.MessageType.TEXT) {
            if (request.getContent() == null || request.getContent().isBlank()) {
                throw new IllegalArgumentException("content is required for TEXT message");
            }
        }

        Message message = new Message();
        message.setSenderId(request.getSenderId());
        message.setReceiverId(request.getReceiverId());
        message.setType(type);
        message.setContent(request.getContent());
        message.setResourceId(request.getResourceId());
        message.setVideoResourceId(request.getVideoResourceId());
        message.setStatus("SENT");

        Message saved = messageRepository.save(message);

        List<MessageDto> pushed = toDtos(List.of(saved));
        if (!pushed.isEmpty()) {
            webSocketPushService.pushNewMessage(saved.getReceiverId(), pushed.get(0));
        }

        MessageSendResponse response = new MessageSendResponse();
        response.setMessageId(saved.getId());
        response.setStatus(saved.getStatus());
        response.setCreatedAt(saved.getCreateTime());
        return response;
    }

    public List<MessageDto> poll(Long userId, Long lastMessageId) {
        List<Message> messages = (lastMessageId == null)
                ? messageRepository.findByReceiverIdOrderByIdAsc(userId)
                : messageRepository.findByReceiverIdAndIdGreaterThanOrderByIdAsc(userId, lastMessageId);

        return toDtos(messages);
    }

    public MessageHistoryResponse history(Long userId, Long peerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Message> result = messageRepository.findConversation(userId, peerId, pageable);

        MessageHistoryResponse response = new MessageHistoryResponse();
        response.setData(toDtos(result.getContent()));

        Pagination pagination = new Pagination();
        pagination.setPage(page);
        pagination.setSize(size);
        pagination.setTotal(result.getTotalElements());
        response.setPagination(pagination);
        return response;
    }

    @Transactional
    public void markRead(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        message.setStatus("READ");
        message.setReadTime(LocalDateTime.now());
        Message saved = messageRepository.save(message);

        List<MessageDto> pushed = toDtos(List.of(saved));
        if (!pushed.isEmpty()) {
            webSocketPushService.pushNewMessage(saved.getSenderId(), pushed.get(0));
        }
    }

    private List<MessageDto> toDtos(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Set<Long> resourceIds = new HashSet<>();
        for (Message m : messages) {
            if (m.getResourceId() != null) {
                resourceIds.add(m.getResourceId());
            }
            if (m.getVideoResourceId() != null) {
                resourceIds.add(m.getVideoResourceId());
            }
        }

        Map<Long, FileResource> resourcesById = resourceIds.isEmpty()
                ? Map.of()
                : fileResourceRepository.findAllById(resourceIds).stream()
                .collect(Collectors.toMap(FileResource::getId, Function.identity()));

        List<MessageDto> dtos = new ArrayList<>(messages.size());
        for (Message m : messages) {
            MessageDto dto = new MessageDto();
            dto.setId(m.getId());
            dto.setSenderId(m.getSenderId());
            dto.setReceiverId(m.getReceiverId());
            dto.setType(m.getType().name());
            dto.setContent(m.getContent());
            dto.setResourceId(m.getResourceId());
            dto.setVideoResourceId(m.getVideoResourceId());
            dto.setStatus(m.getStatus());
            dto.setCreatedAt(m.getCreateTime());

            if (m.getResourceId() != null) {
                FileResource r = resourcesById.get(m.getResourceId());
                if (r != null) {
                    if (!"VideoCoverPending".equals(r.getSourceType())) {
                    dto.setCoverUrl(storageUrlService.toClientUrl(r.getId(), r.getStoragePath()));
                    }
                }
            }
            if (m.getVideoResourceId() != null) {
                FileResource r = resourcesById.get(m.getVideoResourceId());
                if (r != null) {
                    dto.setVideoUrl(storageUrlService.toClientUrl(r.getId(), r.getStoragePath()));
                }
            }

            dtos.add(dto);
        }

        return dtos;
    }
}
