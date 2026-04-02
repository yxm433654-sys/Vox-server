package com.vox.infrastructure.message;

import com.vox.infrastructure.media.MediaAssembler;
import com.vox.infrastructure.message.dto.MessageDto;
import com.vox.infrastructure.message.dto.MessageMediaDto;
import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageMediaAssembler {

    private final MediaAssembler mediaAssembler;

    public MessageMediaDto buildMediaDto(
            Message message, FileResource coverResource, FileResource playResource) {
        MediaAssembler.MediaInfo info = mediaAssembler.assemble(message, coverResource, playResource);
        return toDto(info);
    }

    public void applyResolvedUrls(MessageDto dto, MessageMediaDto media) {
        if (dto == null) return;
        dto.setCoverUrl(media == null ? null : media.getCoverUrl());
        dto.setVideoUrl(media == null ? null : media.getPlayUrl());
    }

    private MessageMediaDto toDto(MediaAssembler.MediaInfo info) {
        if (info == null) return null;
        MessageMediaDto dto = new MessageMediaDto();
        dto.setMediaKind(info.getMediaKind());
        dto.setProcessingStatus(info.getProcessingStatus());
        dto.setResourceId(info.getResourceId());
        dto.setCoverResourceId(info.getCoverResourceId());
        dto.setPlayResourceId(info.getPlayResourceId());
        dto.setCoverUrl(info.getCoverUrl());
        dto.setPlayUrl(info.getPlayUrl());
        dto.setWidth(info.getWidth());
        dto.setHeight(info.getHeight());
        dto.setDuration(info.getDuration());
        dto.setAspectRatio(info.getAspectRatio());
        dto.setSourceType(info.getSourceType());
        return dto;
    }
}
