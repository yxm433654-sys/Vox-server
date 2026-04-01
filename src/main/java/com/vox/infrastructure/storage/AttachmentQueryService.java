package com.vox.infrastructure.storage;

import com.vox.infrastructure.persistence.entity.FileResource;
import com.vox.infrastructure.persistence.repository.FileResourceRepository;
import com.vox.domain.attachment.AttachmentSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttachmentQueryService {

    private final FileResourceRepository fileResourceRepository;
    private final AttachmentSummaryMapper attachmentSummaryMapper;

    public AttachmentSummary getAttachmentInfo(Long id) {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        return attachmentSummaryMapper.toSummary(resource);
    }
}

