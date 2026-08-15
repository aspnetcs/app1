package com.webchat.platformapi.file;

import com.webchat.platformapi.asset.MinioAssetService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileLibraryService {

    public record UploadResult(FileEntity entity, String url, int expiresInSeconds) {}

    private final FileRepository fileRepo;
    private final FileRefRepository refRepo;
    private final MinioAssetService minio;
    private final long maxSizeBytes;

    public FileLibraryService(
            FileRepository fileRepo,
            FileRefRepository refRepo,
            MinioAssetService minio,
            @Value("${platform.files.max-size-bytes:26214400}") long maxSizeBytes
    ) {
        this.fileRepo = fileRepo;
        this.refRepo = refRepo;
        this.minio = minio;
        this.maxSizeBytes = maxSizeBytes <= 0 ? 26214400L : maxSizeBytes;
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadResult uploadUserFile(UUID userId, String purpose, String originalName, String mimeType, byte[] bytes) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (bytes == null) throw new IllegalArgumentException("bytes is required");
        if (bytes.length <= 0) throw new IllegalArgumentException("empty file");
        if (bytes.length > maxSizeBytes) throw new IllegalArgumentException("file too large");

        String normalizedPurpose = (purpose == null || purpose.isBlank()) ? "chat_attachment" : purpose.trim();
        String safeName = sanitizeFilename(originalName == null ? "" : originalName);
        String normalizedMimeType = FileKind.normalizeMimeType(mimeType, safeName);
        String sha256 = sha256(bytes);

        FileEntity existing = null;
        try {
            existing = fileRepo.findFirstByCreatedByAndSha256AndSizeBytesAndDeletedAtIsNull(userId, sha256, bytes.length).orElse(null);
        } catch (Exception ignored) {
            existing = null;
        }
        if (existing != null) {
            MinioAssetService.ConfirmResult confirmed = minio.confirmAndPresignGet(existing.getObjectKey(), existing.getSizeBytes(), existing.getMimeType(), existing.getSha256());
            return new UploadResult(existing, confirmed.downloadUrl(), confirmed.expiresInSeconds());
        }

        String id = "file_" + UUID.randomUUID().toString().replace("-", "");
        String objectKey = "u/" + userId + "/" + normalizedPurpose + "/" + java.time.LocalDate.now().toString().replace("-", "")
                + "/" + id + "_" + safeName;

        minio.uploadStream(objectKey, new ByteArrayInputStream(bytes), bytes.length, normalizedMimeType);
        MinioAssetService.ConfirmResult confirmed = minio.confirmAndPresignGet(objectKey, bytes.length, normalizedMimeType, sha256);

        FileEntity entity = new FileEntity();
        entity.setId(id);
        entity.setCreatedBy(userId);
        entity.setPurpose(normalizedPurpose);
        entity.setOriginalName(safeName);
        entity.setSizeBytes(confirmed.size());
        entity.setMimeType(FileKind.normalizeMimeType(confirmed.mimeType(), safeName));
        entity.setSha256(confirmed.sha256());
        entity.setKind(FileKind.fromMimeTypeOrName(entity.getMimeType(), safeName));
        entity.setBucket(confirmed.bucket());
        entity.setObjectKey(confirmed.objectKey());
        entity.setCreatedAt(Instant.now());

        fileRepo.save(entity);

        return new UploadResult(entity, confirmed.downloadUrl(), confirmed.expiresInSeconds());
    }

    public FileEntity getUserFileOrNull(UUID userId, String fileId) {
        if (userId == null || fileId == null || fileId.isBlank()) return null;
        return fileRepo.findByIdAndCreatedByAndDeletedAtIsNull(fileId.trim(), userId).orElse(null);
    }

    public java.util.Map<String, Object> presignUrl(FileEntity entity) {
        if (entity == null) throw new IllegalArgumentException("file not found");
        MinioAssetService.ConfirmResult confirmed = minio.confirmAndPresignGet(entity.getObjectKey(), entity.getSizeBytes(), entity.getMimeType(), entity.getSha256());
        return java.util.Map.of(
                "url", confirmed.downloadUrl(),
                "expiresIn", confirmed.expiresInSeconds()
        );
    }

    public java.util.List<FileRefEntity> listRefs(String fileId) {
        if (fileId == null || fileId.isBlank()) return java.util.List.of();
        return refRepo.findTop200ByFileIdOrderByIdAsc(fileId.trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public DeleteResult deleteUserFile(UUID userId, String fileId) {
        if (userId == null) throw new IllegalArgumentException("unauthorized");
        if (fileId == null || fileId.isBlank()) throw new IllegalArgumentException("fileId is required");

        FileEntity entity = fileRepo.findByIdAndCreatedByAndDeletedAtIsNull(fileId.trim(), userId).orElse(null);
        if (entity == null) return DeleteResult.notFound();

        long refCount = refRepo.countByFileId(entity.getId());
        if (refCount > 0) {
            return DeleteResult.referenced(listRefs(entity.getId()));
        }

        fileRepo.softDelete(entity.getId(), Instant.now());
        return DeleteResult.deleted("soft");
    }

    @Transactional(rollbackFor = Exception.class)
    public DeleteResult adminDelete(String fileId, String mode) {
        if (fileId == null || fileId.isBlank()) throw new IllegalArgumentException("fileId is required");
        String normalizedMode = (mode == null || mode.isBlank()) ? "soft" : mode.trim().toLowerCase(Locale.ROOT);

        FileEntity entity = fileRepo.findById(fileId.trim()).orElse(null);
        if (entity == null) return DeleteResult.notFound();

        long refCount = refRepo.countByFileId(entity.getId());
        if (refCount > 0 && !"hard".equals(normalizedMode)) {
            return DeleteResult.referenced(listRefs(entity.getId()));
        }

        if ("hard".equals(normalizedMode)) {
            // Delete refs first to avoid FK issues if added later.
            try { refRepo.deleteByFileId(entity.getId()); } catch (Exception ignored) {}
            try { minio.removeObject(entity.getObjectKey()); } catch (Exception ignored) {}
            fileRepo.deleteById(entity.getId());
            return DeleteResult.deleted("hard");
        }

        fileRepo.softDelete(entity.getId(), Instant.now());
        return DeleteResult.deleted("soft");
    }

    public record DeleteResult(boolean deleted, String mode, String reason, java.util.List<java.util.Map<String, Object>> refs) {
        static DeleteResult deleted(String mode) {
            return new DeleteResult(true, mode, "", java.util.List.of());
        }

        static DeleteResult notFound() {
            return new DeleteResult(false, "", "not_found", java.util.List.of());
        }

        static DeleteResult referenced(java.util.List<FileRefEntity> refs) {
            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            if (refs != null) {
                for (FileRefEntity r : refs) {
                    out.add(java.util.Map.of(
                            "refType", r.getRefType(),
                            "refId", r.getRefId()
                    ));
                }
            }
            return new DeleteResult(false, "", "referenced", out);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(bytes);
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("sha256 compute failed", e);
        }
    }

    private static String sanitizeFilename(String filename) {
        String f = filename.trim();
        if (f.isEmpty()) return "file";
        f = f.replace("\\", "_").replace("/", "_");
        if (f.length() > 120) f = f.substring(f.length() - 120);
        return f;
    }
}

