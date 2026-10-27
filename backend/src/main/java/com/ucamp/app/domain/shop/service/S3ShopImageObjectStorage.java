package com.ucamp.app.domain.shop.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/** 비공개 S3 버킷에 저장하고 백엔드가 이미지를 중계한다. 공개 ACL이나 만료 URL을 DB에 저장하지 않는다. */
@Component
@ConditionalOnProperty(name = "app.shop-image.storage", havingValue = "s3", matchIfMissing = true)
public class S3ShopImageObjectStorage implements ShopImageObjectStorage {

    private final S3Client client;
    private final String bucket;
    private final String prefix;

    public S3ShopImageObjectStorage(S3Client client,
            @Value("${app.shop-image.s3.bucket:}") String bucket,
            @Value("${app.shop-image.s3.prefix:shop-images/}") String prefix) {
        this.client = client;
        this.bucket = bucket;
        if (prefix == null || prefix.length() > 300 || !prefix.matches("[a-zA-Z0-9][a-zA-Z0-9/_-]*")) {
            throw new IllegalArgumentException("S3 이미지 prefix는 영문·숫자로 시작하는 경로여야 합니다.");
        }
        this.prefix = prefix.endsWith("/") ? prefix : prefix + "/";
        if (scope().length() > 512) {
            throw new IllegalArgumentException("S3 이미지 저장소 설정이 너무 깁니다.");
        }
    }

    @Override
    public String scope() {
        return "s3:" + bucket + "/" + prefix;
    }

    @Override
    public void checkReady() {
        requireBucket();
    }

    @Override
    public void put(String fileName, byte[] content, String contentType) {
        requireBucket();
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(prefix + fileName)
                    .contentType(contentType).contentLength((long) content.length).build(),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3에 이미지를 저장하지 못했습니다.", e);
        }
    }

    @Override
    public byte[] read(String fileName) {
        requireBucket();
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(prefix + fileName).build())
                    .asByteArray();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage(), e);
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 이미지를 읽지 못했습니다.", e);
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 이미지를 읽지 못했습니다.", e);
        }
    }

    @Override
    public void delete(String fileName) {
        requireBucket();
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(prefix + fileName).build());
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 이미지를 정리하지 못했습니다.", e);
        }
    }

    private void requireBucket() {
        // 키·버킷 없이도 앱 기동과 다른 업무는 가능하며 이미지 요청만 명확하게 실패시킨다.
        if (bucket == null || bucket.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 이미지 버킷 설정이 필요합니다.");
        }
    }
}
