package vn.smarttask.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MinioConfigTest {
  private final MinioConfig config = new MinioConfig();

  @Test void localStorageStartsWithoutMinioCredentials() {
    assertNotNull(config.minioClient("http://127.0.0.1:9000", "", "", "local"));
  }

  @Test void s3StorageRejectsMissingCredentials() {
    assertThrows(IllegalArgumentException.class,
        () -> config.minioClient("http://127.0.0.1:9000", "", "", "s3"));
  }

  @Test void s3StorageAcceptsConfiguredCredentialsWithoutConnecting() {
    assertNotNull(config.minioClient("http://127.0.0.1:9000", "test-access", "test-secret", "s3"));
  }
}
