package com.chatapp.service.file;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.entity.FileResource;
import com.chatapp.repository.FileResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileQueryService {

    private final FileResourceRepository fileResourceRepository;
    private final FileResponseMapper fileResponseMapper;

    public FileUploadResponse getFileInfo(Long id) {
        FileResource resource = fileResourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        return fileResponseMapper.toFileInfo(resource);
    }
}
