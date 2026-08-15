package com.webchat.platformapi.file;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileV1ControllerTest {

    @Mock
    private FileLibraryService fileLibraryService;

    private MockMvc mockMvcEnabled;
    private MockMvc mockMvcDisabled;

    @BeforeEach
    void setUp() {
        mockMvcEnabled = MockMvcBuilders.standaloneSetup(new FileV1Controller(fileLibraryService, true)).build();
        mockMvcDisabled = MockMvcBuilders.standaloneSetup(new FileV1Controller(fileLibraryService, false)).build();
    }

    @Test
    void uploadRejectsWhenDisabled() throws Exception {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());

        mockMvcDisabled.perform(
                        multipart("/api/v1/files")
                                .file(file)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SERVER_ERROR));
    }

    @Test
    void uploadReturnsFileMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hi".getBytes());

        FileEntity entity = new FileEntity();
        entity.setId("file_1");
        entity.setCreatedBy(userId);
        entity.setPurpose("chat_attachment");
        entity.setOriginalName("a.txt");
        entity.setSizeBytes(2);
        entity.setMimeType("text/plain");
        entity.setSha256("aa");
        entity.setKind(FileKind.DOCUMENT);
        entity.setBucket("b");
        entity.setObjectKey("k");
        entity.setCreatedAt(Instant.now());

        when(fileLibraryService.uploadUserFile(eq(userId), anyString(), anyString(), anyString(), any(byte[].class)))
                .thenReturn(new FileLibraryService.UploadResult(entity, "https://signed", 600));

        mockMvcEnabled.perform(
                        multipart("/api/v1/files")
                                .file(file)
                                .param("purpose", "chat_attachment")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value("file_1"))
                .andExpect(jsonPath("$.data.url").value("https://signed"));
    }

    @Test
    void getReturnsMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        FileEntity entity = new FileEntity();
        entity.setId("file_1");
        entity.setCreatedBy(userId);
        entity.setPurpose("chat_attachment");
        entity.setOriginalName("a.txt");
        entity.setSizeBytes(2);
        entity.setMimeType("text/plain");
        entity.setSha256("aa");
        entity.setKind(FileKind.DOCUMENT);
        entity.setBucket("b");
        entity.setObjectKey("k");
        entity.setCreatedAt(Instant.parse("2026-04-18T00:00:00Z"));

        when(fileLibraryService.getUserFileOrNull(userId, "file_1")).thenReturn(entity);

        mockMvcEnabled.perform(
                        get("/api/v1/files/file_1")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value("file_1"));
    }

    @Test
    void deleteReturnsRefsWhenReferenced() throws Exception {
        UUID userId = UUID.randomUUID();

        FileLibraryService.DeleteResult referenced = new FileLibraryService.DeleteResult(
                false, "", "referenced",
                List.of(Map.of("refType", "chat_message", "refId", "msg_1"))
        );
        when(fileLibraryService.deleteUserFile(userId, "file_1")).thenReturn(referenced);

        mockMvcEnabled.perform(
                        delete("/api/v1/files/file_1")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andExpect(jsonPath("$.data.reason").value("referenced"));
    }
}

