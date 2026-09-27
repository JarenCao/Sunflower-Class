package com.sunflower_class.config;

import io.minio.MinioClient;
import jakarta.validation.constraints.NotBlank;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * S3 兼容对象存储配置，提供访问端点、凭据、桶名及客户端实例。
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
@Validated
public class MinioConfig {

    @NotBlank(message = "minio.endpoint 不能为空")
    private String endpoint;

    @NotBlank(message = "minio.accessKey 不能为空")
    private String accessKey;

    @NotBlank(message = "minio.secretKey 不能为空")
    private String secretKey;

    private String files = "mediafiles";

    private String videofiles = "video";

    private int connectTimeout = 10000;

    private int writeTimeout = 10000;

    private int readTimeout = 10000;

    /**
     * 根据外部配置的地址与凭据创建 S3 兼容客户端，用于访问 Docker 中的对象存储。
     */
    @Bean
    public MinioClient minioClient() {
        log.info("初始化 MinIO 客户端，连接地址: {}", endpoint);

        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
            .build();

        MinioClient client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .httpClient(httpClient)
            .build();

        log.info("MinIO 客户端初始化完成");
        return client;
    }
}
