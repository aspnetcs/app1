package com.webchat.platformapi.backup;

import com.webchat.platformapi.file.FileEntity;
import com.webchat.platformapi.file.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupManifestServiceTest {

    @Mock
    private FileRepository fileRepository;

    private BackupManifestService service;

    @BeforeEach
    void setUp() {
        service = new BackupManifestService(fileRepository);
    }

    @Test
    void buildUserManifestUsesFileRepoFilteredByUser() {
        UUID userId = UUID.randomUUID();

        FileEntity file = new FileEntity();
        file.setId("file_0123456789abcdef0123456789abcdef");
        file.setCreatedBy(userId);
        file.setOriginalName("demo.txt");
        file.setSizeBytes(12L);
        file.setMimeType("text/plain");
        file.setSha256("abc");
        file.setKind("document");
        file.setCreatedAt(Instant.parse("2026-04-19T00:00:00Z"));

        when(fileRepository.adminSearch(eq(null), eq(null), eq(userId), eq(false), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        Map<String, Object> manifest = service.buildUserManifest(userId, 200);

        assertEquals(BackupManifestService.BACKUP_SCHEMA_VERSION, manifest.get("schemaVersion"));
        assertNotNull(manifest.get("exportedAt"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fileRefs = (List<Map<String, Object>>) manifest.get("fileRefs");
        assertEquals(1, fileRefs.size());
        assertEquals(file.getId(), fileRefs.get(0).get("fileId"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(fileRepository).adminSearch(eq(null), eq(null), eq(userId), eq(false), pageable.capture());
        assertEquals(200, pageable.getValue().getPageSize());
    }
}

