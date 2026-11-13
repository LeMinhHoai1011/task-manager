package vn.smarttask.files.service;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.smarttask.common.AppException;
import vn.smarttask.files.StorageErrors;

/**
 * F1 file bytes reader (no direct storage URLs ever leave the backend).
 * Prefers MinIO; falls back to the local gateway root when MinIO is
 * unreachable; otherwise 409 {@code FILE_OBJECT_MISSING} when the key is
 * really gone, or 503 {@code STORAGE_UNAVAILABLE} when storage itself is
 * down (clean envelope, never a 500 stack leak). Lives in a
 * service so controllers stay free of {@code io.minio} (ArchRules).
 */
@Service
public class FileContentService {
  private final MinioClient minio;
  private final String bucket;
  private final Path localRoot;
  @Value("${file.gateway.mode:local}")
  private String storageMode = "s3";

  public FileContentService(MinioClient minio,
      @Value("${file.gateway.minio-bucket:smarttask}") String bucket,
      @Value("${file.gateway.local-root:./var/files}") String localRoot) {
    this.minio = minio;
    this.bucket = bucket;
    // Normalize once: a configured "./var/files" root must compare equal to
    // normalized resolutions, otherwise the traversal guard below rejects
    // every legitimate local fallback (startsWith mismatch on the "." segment).
    this.localRoot = Paths.get(localRoot).toAbsolutePath().normalize();
  }

  public record Content(byte[] bytes, String contentType) {}

  public Content read(String objectKey, String bucketOverride, String contentType) {
    if ("local".equalsIgnoreCase(storageMode)) {
      Path local = localRoot.resolve(objectKey).normalize();
      if (!local.startsWith(localRoot) || !Files.isRegularFile(local)) throw missing(objectKey);
      try { return new Content(Files.readAllBytes(local), contentType); }
      catch (java.io.IOException e) { throw StorageErrors.unavailable(objectKey, e); }
    }
    String b = bucketOverride != null ? bucketOverride : bucket;
    Exception s3failure = null;
    try {
      try (InputStream in = minio.getObject(GetObjectArgs.builder()
          .bucket(b).object(objectKey).build())) {
        return new Content(in.readAllBytes(), contentType);
      }
    } catch (Exception e) {
      s3failure = e;
    }
    Path local = localRoot.resolve(objectKey).normalize();
    if (local.startsWith(localRoot) && Files.isRegularFile(local)) {
      try {
        return new Content(Files.readAllBytes(local), contentType);
      } catch (Exception e) {
        throw missing(objectKey);
      }
    }
    // Local fallback missed too: map the S3 failure — really-gone key is
    // 409 FILE_OBJECT_MISSING, connectivity failure is 503
    // STORAGE_UNAVAILABLE. Never a raw 500 stack.
    if (s3failure != null) {
      throw StorageErrors.map(s3failure, objectKey);
    }
    throw missing(objectKey);
  }

  private static AppException missing(String objectKey) {
    return AppException.stateConflict("FILE_OBJECT_MISSING",
        "Stored object missing for key " + objectKey);
  }
}
