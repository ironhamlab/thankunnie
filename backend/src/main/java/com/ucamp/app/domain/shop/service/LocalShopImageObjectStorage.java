package com.ucamp.app.domain.shop.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/** 명시적으로 local 저장소를 선택한 개발·테스트 환경의 파일 저장소. */
@Component
@ConditionalOnProperty(name = "app.shop-image.storage", havingValue = "local")
public class LocalShopImageObjectStorage implements ShopImageObjectStorage {

    private final Path directory;

    public LocalShopImageObjectStorage(
            @Value("${app.shop-image.directory:${user.home}/.thankunnie/shop-images}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void put(String fileName, byte[] content, String contentType) {
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다.");
        }
    }

    @Override
    public byte[] read(String fileName) {
        try {
            return Files.readAllBytes(directory.resolve(fileName));
        } catch (NoSuchFileException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지를 읽지 못했습니다.");
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            Files.deleteIfExists(directory.resolve(fileName));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지 파일을 정리하지 못했습니다.");
        }
    }
}
