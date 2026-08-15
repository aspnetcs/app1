package com.webchat.platformapi.asset;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MinioAssetService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(30);
    private static final java.util.regex.Pattern SHA256_HEX = java.util.regex.Pattern.compile("^[a-fA-F0-9]{64}$");

    private final MinioClient internalMinio;
    private final MinioClient presignMinio;
    private final String bucket;
    private final AtomicBoolean bucketChecked = new AtomicBoolean(false);

    public MinioAssetService(
            @Value("${minio.endpoint}") String internalEndpoint,
            @Value("${minio.public-endpoint:}") String publicEndpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket:webchat}") String bucket
    ) {
        if (publicEndpoint == null || publicEndpoint.isBlank()) publicEndpoint = internalEndpoint;

        this.internalMinio = MinioClient.builder()
                .endpoint(internalEndpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.presignMinio = MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket == null || bucket.isBlank() ? "webchat" : bucket.trim();
    }

    public record PresignResult(String bucket, String objectKey, String uploadUrl, int expiresInSeconds) {}

    public record ConfirmResult(String bucket, String objectKey, String downloadUrl, int expiresInSeconds, long size, String mimeType, String sha256) {}

    public PresignResult presignPut(UUID userId, String purpose, String filename) {
        ensureBucket();

        String safeFilename = sanitizeFilename(filename == null ? "" : filename);
        String date = LocalDate.now().toString().replace("-", "");
        String objectKey = "u/" + userId + "/" + purpose + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + "_" + safeFilename;

        try {
            String url = presignMinio.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) PRESIGN_TTL.toSeconds(), TimeUnit.SECONDS)
                            .build()
            );
            return new PresignResult(bucket, objectKey, url, (int) PRESIGN_TTL.toSeconds());
        } catch (Exception e) {
            throw new AssetException("presign failed: " + e.getMessage(), e);
        }
    }

    public ConfirmResult confirmAndPresignGet(String objectKey, long expectedSize, String expectedMimeType, String expectedSha256) {
        ensureBucket();
        String normalizedExpectedSha256 = normalizeSha256(expectedSha256);

        StatObjectResponse stat;
        try {
            stat = internalMinio.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new AssetException("object not found", e);
        }

        long size = stat.size();
        if (expectedSize > 0 && size != expectedSize) {
            throw new AssetException("size mismatch");
        }

        String mime = stat.contentType();
        if (expectedMimeType != null && !expectedMimeType.isBlank()) {
            if (mime == null || mime.isBlank()) {
                throw new AssetException("mimeType missing on object");
            }
            if (!mime.equalsIgnoreCase(expectedMimeType.trim())) {
                throw new AssetException("mimeType mismatch");
            }
        }

        String actual = sha256OfObject(objectKey);
        if (!actual.equalsIgnoreCase(normalizedExpectedSha256)) {
            throw new AssetException("sha256 mismatch");
        }
        normalizedExpectedSha256 = actual.toLowerCase(Locale.ROOT);

        try {
            String url = presignMinio.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry((int) DOWNLOAD_TTL.toSeconds(), TimeUnit.SECONDS)
                            .build()
            );
            return new ConfirmResult(bucket, objectKey, url, (int) DOWNLOAD_TTL.toSeconds(), size, mime == null ? "" : mime, normalizedExpectedSha256);
        } catch (Exception e) {
            throw new AssetException("presign get failed", e);
        }
    }

    static String normalizeSha256(String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            throw new AssetException("sha256 required");
        }
        String normalized = sha256.trim();
        if (!SHA256_HEX.matcher(normalized).matches()) {
            throw new AssetException("sha256 invalid");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String sha256OfObject(String objectKey) {
        try (InputStream in = internalMinio.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (read == 0) continue;
                md.update(buf, 0, read);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new AssetException("sha256 compute failed", e);
        }
    }

    public void uploadStream(String objectKey, InputStream in, long size, String contentType) {
        ensureBucket();
        try {
            internalMinio.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(in, size, -1)
                            .contentType(contentType == null ? "application/octet-stream" : contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new AssetException("upload failed: " + e.getMessage(), e);
        }
    }

    public byte[] downloadBytes(String objectKey, long maxBytes) {
        ensureBucket();
        try (InputStream in = internalMinio.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buf)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (maxBytes > 0 && total > maxBytes) throw new AssetException("object too large");
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (AssetException e) {
            throw e;
        } catch (Exception e) {
            throw new AssetException("download failed: " + e.getMessage(), e);
        }
    }

    public void removeObject(String objectKey) {
        ensureBucket();
        try {
            internalMinio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new AssetException("remove failed: " + e.getMessage(), e);
        }
    }

    private void ensureBucket() {
        if (bucketChecked.get()) return;
        try {
            boolean exists = internalMinio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                internalMinio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketChecked.set(true);
        } catch (Exception e) {
            throw new AssetException("bucket init failed", e);
        }
    }

    private static String sanitizeFilename(String filename) {
        String f = filename.trim();
        if (f.isEmpty()) return "file";
        // prevent path traversal
        f = f.replace("\\", "_").replace("/", "_");
        // keep it readable
        if (f.length() > 120) f = f.substring(f.length() - 120);
        return f;
    }

    public static class AssetException extends RuntimeException {
        public AssetException(String message) {
            super(message);
        }

        public AssetException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
