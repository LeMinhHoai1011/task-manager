package vn.smarttask.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
  @Bean
  MinioClient minioClient(
      @Value("${file.gateway.minio-endpoint:http://localhost:9000}") String endpoint,
      @Value("${file.gateway.minio-access-key:minio}") String accessKey,
      @Value("${file.gateway.minio-secret-key:minio123}") String secretKey,
      @Value("${file.gateway.mode:local}") String mode) {
    var builder = MinioClient.builder().endpoint(endpoint);
    if ("s3".equalsIgnoreCase(mode) || "minio".equalsIgnoreCase(mode)) {
      if (accessKey.isBlank() || secretKey.isBlank()) {
        throw new IllegalArgumentException("S3 storage requires configured access and secret keys");
      }
      builder.credentials(accessKey, secretKey);
    } else if (!accessKey.isBlank() && !secretKey.isBlank()) {
      builder.credentials(accessKey, secretKey);
    }
    return builder.build();
  }
}
