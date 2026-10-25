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
      @Value("${file.gateway.minio-secret-key:minio123}") String secretKey) {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }
}
