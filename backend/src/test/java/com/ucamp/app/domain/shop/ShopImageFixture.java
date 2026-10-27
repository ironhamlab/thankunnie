package com.ucamp.app.domain.shop;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.mock.web.MockMultipartFile;

/** 외부 파일 없이 실제 디코딩 가능한 테스트 이미지를 만든다. */
public final class ShopImageFixture {

    private ShopImageFixture() {
    }

    public static MockMultipartFile image(String format) throws IOException {
        return image(format, new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));
    }

    public static MockMultipartFile image(String format, BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return new MockMultipartFile("file", "image." + format,
                format.equals("png") ? "image/png" : "image/jpeg", output.toByteArray());
    }
}
