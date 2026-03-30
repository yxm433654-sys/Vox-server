package com.chatapp.controller;

import com.chatapp.dto.FileUploadResponse;
import com.chatapp.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @Test
    void upload_allowsOctetStreamWhenFilenameIsKnownImage() throws Exception {
        FileUploadResponse resp = new FileUploadResponse();
        resp.setFileId(1L);
        resp.setFileType("IMAGE");
        when(fileService.handleFileUpload(any(), any())).thenReturn(resp);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "application/octet-stream",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );

        mockMvc.perform(multipart("/api/files/upload").file(file).param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void upload_rejectsUnknownOctetStreamType() throws Exception {
        when(fileService.handleFileUpload(any(), any()))
                .thenThrow(new IllegalArgumentException("Unknown file type"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.bin",
                "application/octet-stream",
                new byte[]{0x01, 0x02, 0x03}
        );

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}

