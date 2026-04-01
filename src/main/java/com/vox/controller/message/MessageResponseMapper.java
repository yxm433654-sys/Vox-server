package com.vox.controller.message;

import com.vox.application.message.MessageHistoryResult;
import com.vox.application.message.MessageMediaView;
import com.vox.application.message.MessagePageResult;
import com.vox.application.message.MessageView;
import com.vox.application.message.SendMessageCommand;
import com.vox.application.message.SendMessageResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MessageResponseMapper {

    public MessageResponse toResponse(MessageView view) {
        if (view == null) {
            return null;
        }
        MessageResponse response = new MessageResponse();
        response.setId(view.getId());
        response.setSenderId(view.getSenderId());
        response.setReceiverId(view.getReceiverId());
        response.setType(view.getType());
        response.setContent(view.getContent());
        response.setResourceId(view.getResourceId());
        response.setVideoResourceId(view.getVideoResourceId());
        response.setCoverUrl(view.getCoverUrl());
        response.setVideoUrl(view.getVideoUrl());
        response.setMedia(toMediaResponse(view.getMedia()));
        response.setStatus(view.getStatus());
        response.setCreatedAt(view.getCreatedAt());
        return response;
    }

    public List<MessageResponse> toResponses(List<MessageView> views) {
        return views.stream().map(this::toResponse).toList();
    }

    public MessageHistoryResponse toHistoryResponse(MessageHistoryResult result) {
        MessageHistoryResponse response = new MessageHistoryResponse();
        response.setData(toResponses(result.getData()));
        response.setPagination(toPaginationResponse(result.getPagination()));
        return response;
    }

    public SendMessageResponse toSendResponse(SendMessageResult result) {
        if (result == null) {
            return null;
        }
        SendMessageResponse response = new SendMessageResponse();
        response.setMessageId(result.getMessageId());
        response.setStatus(result.getStatus());
        response.setCreatedAt(result.getCreatedAt());
        return response;
    }

    public SendMessageCommand toCommand(SendMessageRequest request) {
        SendMessageCommand command = new SendMessageCommand();
        command.setSenderId(request.getSenderId());
        command.setReceiverId(request.getReceiverId());
        command.setType(request.getType());
        command.setContent(request.getContent());
        command.setResourceId(request.getResourceId());
        command.setVideoResourceId(request.getVideoResourceId());
        return command;
    }

    private MessageMediaResponse toMediaResponse(MessageMediaView view) {
        if (view == null) {
            return null;
        }
        MessageMediaResponse response = new MessageMediaResponse();
        response.setMediaKind(view.getMediaKind());
        response.setProcessingStatus(view.getProcessingStatus());
        response.setResourceId(view.getResourceId());
        response.setCoverResourceId(view.getCoverResourceId());
        response.setPlayResourceId(view.getPlayResourceId());
        response.setCoverUrl(view.getCoverUrl());
        response.setPlayUrl(view.getPlayUrl());
        response.setWidth(view.getWidth());
        response.setHeight(view.getHeight());
        response.setDuration(view.getDuration());
        response.setAspectRatio(view.getAspectRatio());
        response.setSourceType(view.getSourceType());
        return response;
    }

    private MessagePaginationResponse toPaginationResponse(MessagePageResult pageResult) {
        if (pageResult == null) {
            return null;
        }
        MessagePaginationResponse response = new MessagePaginationResponse();
        response.setPage(pageResult.getPage());
        response.setSize(pageResult.getSize());
        response.setTotal(pageResult.getTotal());
        return response;
    }
}
