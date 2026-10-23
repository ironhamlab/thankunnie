package com.ucamp.app.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * boolean 필드를 CHAR(1) 'Y'/'N' 컬럼으로 저장한다.
 *
 * <p>사용: {@code @Convert(converter = BooleanToYnConverter.class)}.
 * 전역 자동 적용은 하지 않는다(autoApply = false). Y/N 컬럼에만 명시적으로 붙인다.
 */
@Converter
public class BooleanToYnConverter implements AttributeConverter<Boolean, String> {

    private static final String YES = "Y";
    private static final String NO = "N";

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? YES : NO;
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData.trim()) {
            case YES -> Boolean.TRUE;
            case NO -> Boolean.FALSE;
            default -> throw new IllegalStateException("Y/N 컬럼에 허용되지 않은 값: " + dbData);
        };
    }
}