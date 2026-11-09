package com.ucamp.app.domain.shop.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 대표 이미지 전용 S3 클라이언트. 자격 증명은 SDK 기본 공급자 체인에서 호출 시점에 읽는다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.shop-image.storage", havingValue = "s3", matchIfMissing = true)
public class ShopImageS3Configuration {

    @Bean(destroyMethod = "close")
    public S3Client shopImageS3Client(@Value("${app.shop-image.s3.region:ap-northeast-2}") String region) {
        S3Client client = S3Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(15)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(20)))
                .build();
        try {
            client.serviceClientConfiguration().endpointOverride().ifPresent(endpoint -> {
                if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
                    throw new IllegalArgumentException("S3 엔드포인트 재정의는 HTTPS만 허용합니다.");
                }
            });
            return client;
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
    }
}
