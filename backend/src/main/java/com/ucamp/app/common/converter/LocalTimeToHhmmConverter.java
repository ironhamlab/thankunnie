package com.ucamp.app.common.converter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * LocalTime 필드를 VARCHAR2(5) 'HH:MM' 컬럼으로 저장한다.
 *
 * <p>사용: {@code @Convert(converter = LocalTimeToHhmmConverter.class)}. 전역 자동 적용은 하지 않는다.
 */
@Converter
public class LocalTimeToHhmmConverter implements AttributeConverter<LocalTime, String> {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public String convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null ? null : attribute.format(HH_MM);
    }

    @Override
    public LocalTime convertToEntityAttribute(String dbData) {
        return dbData == null ? null : LocalTime.parse(dbData.trim(), HH_MM);
    }
}
