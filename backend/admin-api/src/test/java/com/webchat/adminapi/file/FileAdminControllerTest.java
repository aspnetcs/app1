package com.webchat.adminapi.file;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.file.FileEntity;
import com.webchat.platformapi.file.FileLibraryService;
import com.webchat.platformapi.file.FileRefEntity;
import com.webchat.platformapi.file.FileRefRepository;
import com.webchat.platformapi.file.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileAdminControllerTest {

    @Mock
    private FileRepository fileRepo;

    @Mock
    private FileRefRepository refRepo;

    @Mock
    private FileLibraryService fileLibraryService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FileAdminController(fileRepo, refRepo, fileLibraryService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void listRejectsNonAdminRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(fileRepo);
    }

    @Test
    void listReturnsPagedEnvelope() throws Exception {
        FileEntity entity = new FileEntity();
        entity.setId("file_1");
        entity.setCreatedBy(UUID.randomUUID());
        entity.setPurpose("chat_attachment");
        entity.setOriginalName("a.txt");
        entity.setSizeBytes(2);
        entity.setMimeType("text/plain");
        entity.setSha256("aa");
        entity.setKind("document");
        entity.setBucket("b");
        entity.setObjectKey("k");
        entity.setCreatedAt(Instant.parse("2026-04-18T00:00:00Z"));

        when(fileRepo.adminSearch(eq("a"), eq("document"), eq(null), eq(true), eq(PageRequest.of(1, 2))))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(1, 2), 11));

        mockMvc.perform(admin(get("/api/v1/admin/files")
                        .param("page", "1")
                        .param("size", "2")
                        .param("keyword", "a")
                        .param("kind", "document")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].fileId").value("file_1"))
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2));

        verify(fileRepo).adminSearch(eq("a"), eq("document"), eq(null), eq(true), eq(PageRequest.of(1, 2)));
    }

    @Test
    void detailReturnsFileMetadata() throws Exception {
        FileEntity entity = new FileEntity();
        entity.setId("file_1");
        entity.setCreatedBy(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        entity.setPurpose("chat_attachment");
        entity.setOriginalName("a.txt");
        entity.setSizeBytes(2);
        entity.setMimeType("text/plain");
        entity.setSha256("aa");
        entity.setKind("document");
        entity.setBucket("b");
        entity.setObjectKey("k");
        entity.setCreatedAt(Instant.parse("2026-04-18T00:00:00Z"));

        when(fileRepo.findById("file_1")).thenReturn(java.util.Optional.of(entity));

        mockMvc.perform(admin(get("/api/v1/admin/files/file_1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileId").value("file_1"))
                .andExpect(jsonPath("$.data.objectKey").value("k"));
    }

    @Test
    void refsReturnsItems() throws Exception {
        FileRefEntity ref = new FileRefEntity();
        ref.setFileId("file_1");
        ref.setRefType("chat_message");
        ref.setRefId("msg_1");
        ref.setCreatedAt(Instant.parse("2026-04-18T00:00:00Z"));

        when(refRepo.countByFileId("file_1")).thenReturn(1L);
        when(refRepo.findTop200ByFileIdOrderByIdAsc("file_1")).thenReturn(List.of(ref));

        mockMvc.perform(admin(get("/api/v1/admin/files/file_1/refs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].refType").value("chat_message"));
    }

    @Test
    void deleteReturnsRefsWhenReferenced() throws Exception {
        when(fileLibraryService.adminDelete(eq("file_1"), any()))
                .thenReturn(new FileLibraryService.DeleteResult(false, "", "referenced", List.of(
                        Map.of("refType", "chat_message", "refId", "msg_1")
                )));

        mockMvc.perform(admin(delete("/api/v1/admin/files/file_1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andExpect(jsonPath("$.data.reason").value("referenced"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
