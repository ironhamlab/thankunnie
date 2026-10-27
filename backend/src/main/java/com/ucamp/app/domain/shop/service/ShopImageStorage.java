package com.ucamp.app.domain.shop.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.dto.response.ShopImageContentResponse;

/** JPEG·PNG 검증 및 저장소 위임. 원본 파일명은 저장 경로에 사용하지 않는다. */
@Slf4j
@Component
public class ShopImageStorage {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private static final String FILE_NAME_PATTERN = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(jpg|png)";

    private final ShopImageObjectStorage objectStorage;
    private final ShopImageCleanupService cleanupService;

    public ShopImageStorage(ShopImageObjectStorage objectStorage, ShopImageCleanupService cleanupService) {
        this.objectStorage = objectStorage;
        this.cleanupService = cleanupService;
    }

    /** 파일을 완전히 검증한 뒤 새 파일로 저장하고 서버가 생성한 파일명을 반환한다. */
    public String save(MultipartFile file) {
        String format = validateMetadata(file);
        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지를 읽지 못했습니다.", e);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw invalidImage();
        }
        validateContent(bytes, format);
        objectStorage.checkReady();

        String fileName = UUID.randomUUID() + "." + format;
        try {
            objectStorage.put(fileName, bytes, format.equals("png") ? "image/png" : "image/jpeg");
            return fileName;
        } catch (RuntimeException e) {
            // S3가 저장을 마친 뒤 응답만 유실된 경우에도 새 객체가 남지 않도록 정리를 시도한다.
            deleteQuietly(fileName);
            throw e;
        }
    }

    /** 서버가 발급한 파일명만 허용하며 이미지 내용과 타입을 반환한다. */
    public ShopImageContentResponse read(String fileName) {
        if (!isStoredFileName(fileName)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new ShopImageContentResponse(objectStorage.read(fileName),
                fileName.endsWith(".png") ? "image/png" : "image/jpeg");
    }

    /** 커밋 후 구파일, 롤백 후 새 파일을 정리한다. 정리 실패로 이미 끝난 DB 결과를 뒤집지 않는다. */
    public void deleteQuietly(String fileName) {
        if (!isStoredFileName(fileName)) {
            return;
        }
        try {
            objectStorage.delete(fileName);
        } catch (RuntimeException e) {
            log.warn("사용하지 않는 매장 이미지 파일 정리 실패: {}", fileName, e);
            try {
                cleanupService.enqueue(objectStorage.scope(), fileName);
            } catch (RuntimeException persistenceFailure) {
                // 정리 기록용 DB도 장애이면 원래 업로드·삭제 결과를 바꾸지 않고 수동 복구 단서를 남긴다.
                log.error("매장 이미지 정리 작업 기록 실패, 수동 정리 필요: {} / {}",
                        objectStorage.scope(), fileName, persistenceFailure);
            }
        }
    }

    /** 경로·외부 URL을 포함하지 않는 서버 발급 파일명인지 검사한다. */
    public boolean isStoredFileName(String fileName) {
        return fileName != null && fileName.matches(FILE_NAME_PATTERN);
    }

    private String validateMetadata(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw invalidImage();
        }
        String name = file.getOriginalFilename();
        if (name == null) {
            throw invalidImage();
        }
        name = name.toLowerCase(Locale.ROOT);
        if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && "image/jpeg".equals(file.getContentType())) {
            return "jpg";
        }
        if (name.endsWith(".png") && "image/png".equals(file.getContentType())) {
            return "png";
        }
        throw invalidImage();
    }

    private void validateContent(byte[] bytes, String format) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                String actualFormat = reader.getFormatName();
                if (!(format.equals("jpg") ? actualFormat.equalsIgnoreCase("JPEG")
                        : actualFormat.equalsIgnoreCase("PNG"))) {
                    throw invalidImage();
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // 압축 파일이 작아도 디코딩에 과도한 메모리를 쓰지 않도록 먼저 해상도를 확인한다.
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw invalidImage();
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalidImage();
                }
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalidImage();
        }
    }

    private BusinessException invalidImage() {
        return new BusinessException(ErrorCode.INVALID_INPUT,
                "대표 이미지는 5MB 이하의 정상적인 JPEG·PNG 파일(최대 1,600만 화소)이어야 합니다.");
    }
}
