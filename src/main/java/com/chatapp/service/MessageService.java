package com.chatapp.service;

import com.chatapp.dto.MessageDto;
import com.chatapp.dto.MessageHistoryResponse;
import com.chatapp.dto.MessageMediaDto;
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

    @Transactional
    public int clearConversation(Long userId, Long peerId) {
        if (userId == null || peerId == null) {
            throw new IllegalArgumentException("userId and peerId are required");
        }
        if (Objects.equals(userId, peerId)) {
            throw new IllegalArgumentException("peerId must be different from userId");
        }
        return messageRepository.deleteConversation(userId, peerId);
    }

    public void pushMediaRefreshByResourceId(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        List<Message> messages = messageRepository.findByResourceIdOrVideoResourceId(resourceId, resourceId);
        if (messages.isEmpty()) {
            return;
        }
        for (MessageDto dto : toDtos(messages)) {
            webSocketPushService.pushNewMessage(dto.getSenderId(), dto);
            if (!Objects.equals(dto.getSenderId(), dto.getReceiverId())) {
                webSocketPushService.pushNewMessage(dto.getReceiverId(), dto);
            }
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

            FileResource coverResource = null;
            FileResource playResource = null;
            if (m.getResourceId() != null) {
                coverResource = resourcesById.get(m.getResourceId());
                if (coverResource != null) {
                    if (!"VideoCoverPending".equals(coverResource.getSourceType())) {
                        dto.setCoverUrl(storageUrlService.toClientUrl(coverResource.getId(), coverResource.getStoragePath()));
                    }
                }
            }
            if (m.getVideoResourceId() != null) {
                playResource = resourcesById.get(m.getVideoResourceId());
                if (playResource != null) {
                    dto.setVideoUrl(storageUrlService.toClientUrl(playResource.getId(), playResource.getStoragePath()));
                }
            }
            dto.setMedia(buildMediaDto(m, coverResource, playResource));

            dtos.add(dto);
        }

        return dtos;
    }

    private MessageMediaDto buildMediaDto(Message message, FileResource coverResource, FileResource playResource) {
        if (message.getType() == Message.MessageType.TEXT) {
            return null;
        }

        MessageMediaDto media = new MessageMediaDto();
        media.setCoverUrl(resolveClientUrl(coverResource, true));
        media.setPlayUrl(resolveClientUrl(playResource, false));
        media.setResourceId(message.getResourceId());
        media.setCoverResourceId(message.getResourceId());
        media.setPlayResourceId(message.getVideoResourceId());

        if (message.getType() == Message.MessageType.IMAGE) {
            FileResource image = coverResource;
            media.setMediaKind("IMAGE");
            media.setProcessingStatus(image == null ? "FAILED" : "READY");
            media.setPlayResourceId(message.getResourceId());
            media.setPlayUrl(resolveClientUrl(image, false));
            media.setWidth(image == null ? null : image.getWidth());
            media.setHeight(image == null ? null : image.getHeight());
            media.setAspectRatio(computeAspectRatio(
                    image == null ? null : image.getWidth(),
                    image == null ? null : image.getHeight()));
            media.setSourceType(image == null ? null : image.getSourceType());
            return media;
        }

        if (message.getType() == Message.MessageType.VIDEO) {
            media.setMediaKind("VIDEO");
            media.setProcessingStatus(resolveVideoStatus(coverResource, playResource));
            media.setWidth(playResource == null ? null : playResource.getWidth());
            media.setHeight(playResource == null ? null : playResource.getHeight());
            media.setDuration(playResource == null ? null : playResource.getDuration());
            media.setAspectRatio(computeAspectRatio(
                    playResource == null ? null : playResource.getWidth(),
                    playResource == null ? null : playResource.getHeight()));
            media.setSourceType(playResource != null ? playResource.getSourceType()
                    : (coverResource == null ? null : coverResource.getSourceType()));
            return media;
        }

        media.setMediaKind("LIVE_PHOTO");
        media.setProcessingStatus(resolveDynamicStatus(coverResource, playResource));
        media.setWidth(playResource == null ? null : playResource.getWidth());
        media.setHeight(playResource == null ? null : playResource.getHeight());
        media.setDuration(playResource == null ? null : playResource.getDuration());
        media.setAspectRatio(computeAspectRatio(
                playResource == null ? null : playResource.getWidth(),
                playResource == null ? null : playResource.getHeight()));
        media.setSourceType(playResource != null ? playResource.getSourceType()
                : (coverResource == null ? null : coverResource.getSourceType()));
        return media;
    }

    private String resolveClientUrl(FileResource resource, boolean skipPendingVideoCover) {
        if (resource == null) {
            return null;
        }
        if (isPendingResource(resource)) {
            return null;
        }
        if (skipPendingVideoCover && "VideoCoverPending".equals(resource.getSourceType())) {
            return null;
        }
        return storageUrlService.toClientUrl(resource.getId(), resource.getStoragePath());
    }

    private String resolveVideoStatus(FileResource coverResource, FileResource playResource) {
        if (playResource == null) {
            return "FAILED";
        }
        if (coverResource == null || "VideoCoverPending".equals(coverResource.getSourceType())) {
            return "PROCESSING";
        }
        if ("VideoCoverFailed".equals(coverResource.getSourceType())) {
            return "READY";
        }
        return "READY";
    }

    private String resolveDynamicStatus(FileResource coverResource, FileResource playResource) {
        if (coverResource == null || playResource == null) {
            return "PROCESSING";
        }
        if (isPendingResource(coverResource) || isPendingResource(playResource)) {
            return "PROCESSING";
        }
        return "READY";
    }

    private boolean isPendingResource(FileResource resource) {
        return resource.getFileSize() == null || resource.getFileSize() <= 0;
    }

    private Float computeAspectRatio(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        return width / (float) height;
    }
}
