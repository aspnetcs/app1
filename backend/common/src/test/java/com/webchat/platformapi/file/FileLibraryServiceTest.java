package com.webchat.platformapi.file;

import com.webchat.platformapi.asset.MinioAssetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileLibraryServiceTest {

    @Test
    void uploadUserFileUploadsAndPersists() {
        FileRepository fileRepo = mock(FileRepository.class);
        FileRefRepository refRepo = mock(FileRefRepository.class);
        MinioAssetService minio = mock(MinioAssetService.class);

        when(fileRepo.findFirstByCreatedByAndSha256AndSizeBytesAndDeletedAtIsNull(any(), anyString(), anyLong()))
                .thenReturn(java.util.Optional.empty());

        when(minio.confirmAndPresignGet(anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    String objectKey = inv.getArgument(0, String.class);
                    long size = inv.getArgument(1, Long.class);
                    String mimeType = inv.getArgument(2, String.class);
                    String sha256 = inv.getArgument(3, String.class);
                    return new MinioAssetService.ConfirmResult(
                            "b", objectKey, "https://signed", 600,
                            size, mimeType, sha256
                    );
                });

        FileLibraryService svc = new FileLibraryService(fileRepo, refRepo, minio, 1024 * 1024);

        UUID userId = UUID.randomUUID();
        byte[] bytes = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FileLibraryService.UploadResult result = svc.uploadUserFile(userId, "chat_attachment", "a.txt", "text/plain", bytes);

        assertNotNull(result);
        assertNotNull(result.entity());
        assertEquals("text/plain", result.entity().getMimeType());
        assertEquals(bytes.length, result.entity().getSizeBytes());
        assertEquals(FileKind.DOCUMENT, result.entity().getKind());

        ArgumentCaptor<FileEntity> saved = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepo).save(saved.capture());
        assertEquals(userId, saved.getValue().getCreatedBy());

        verify(minio).uploadStream(anyString(), any(InputStream.class), eq((long) bytes.length), eq("text/plain"));
    }

    @Test
    void uploadUserFileDedupsWhenSameHashExists() {
        FileRepository fileRepo = mock(FileRepository.class);
        FileRefRepository refRepo = mock(FileRefRepository.class);
        MinioAssetService minio = mock(MinioAssetService.class);

        FileEntity existing = new FileEntity();
        existing.setId("file_x");
        existing.setCreatedBy(UUID.randomUUID());
        existing.setPurpose("chat_attachment");
        existing.setOriginalName("a.txt");
        existing.setSizeBytes(5);
        existing.setMimeType("text/plain");
        existing.setSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        existing.setKind(FileKind.DOCUMENT);
        existing.setBucket("b");
        existing.setObjectKey("k");
        existing.setCreatedAt(java.time.Instant.now());

        when(fileRepo.findFirstByCreatedByAndSha256AndSizeBytesAndDeletedAtIsNull(any(), anyString(), anyLong()))
                .thenReturn(java.util.Optional.of(existing));
        when(minio.confirmAndPresignGet(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new MinioAssetService.ConfirmResult("b", "k", "https://signed", 600, 5, "text/plain", existing.getSha256()));

        FileLibraryService svc = new FileLibraryService(fileRepo, refRepo, minio, 1024 * 1024);
        FileLibraryService.UploadResult result = svc.uploadUserFile(UUID.randomUUID(), "chat_attachment", "a.txt", "text/plain", "hello".getBytes());

        assertEquals(existing.getId(), result.entity().getId());
        verify(minio, never()).uploadStream(anyString(), any(InputStream.class), anyLong(), anyString());
        verify(fileRepo, never()).save(any());
    }
}
