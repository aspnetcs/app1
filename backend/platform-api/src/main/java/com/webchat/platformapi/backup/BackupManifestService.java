package com.webchat.platformapi.backup;

import com.webchat.platformapi.file.FileEntity;
import com.webchat.platformapi.file.FileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BackupManifestService {

    // Keep schema version separate from config-transfer schema.
    public static final int BACKUP_SCHEMA_VERSION = 1;

    private final FileRepository fileRepository;

    public BackupManifestService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public Map<String, Object> buildUserManifest(UUID userId, int limit) {
        if (userId == null) throw new IllegalArgumentException("userId required");

        int resolvedLimit = Math.min(Math.max(limit, 1), 1000);

        var page = fileRepository.adminSearch(
                null,
                null,
                userId,
                false,
                PageRequest.of(0, resolvedLimit)
        );

        List<Map<String, Object>> fileRefs = page.getContent().stream().map(BackupManifestService::toFileRef).toList();
        boolean truncated = page.getTotalElements() > resolvedLimit;

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fileLimit", resolvedLimit);
        meta.put("fileCount", fileRefs.size());
        meta.put("fileTotal", page.getTotalElements());
        meta.put("fileTruncated", truncated);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemaVersion", BACKUP_SCHEMA_VERSION);
        out.put("exportedAt", Instant.now().toString());
        out.put("modules", List.of("files"));
        out.put("fileRefs", fileRefs);
        out.put("meta", meta);
        return out;
    }

    private static Map<String, Object> toFileRef(FileEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fileId", entity.getId());
        item.put("originalName", entity.getOriginalName());
        item.put("size", entity.getSizeBytes());
        item.put("mimeType", entity.getMimeType() == null ? "" : entity.getMimeType());
        item.put("sha256", entity.getSha256() == null ? "" : entity.getSha256());
        item.put("kind", entity.getKind());
        item.put("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        return item;
    }
}

