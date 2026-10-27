package com.ucamp.app.common.exception;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    @DisplayName("5xx 업무 예외는 원인과 함께 ERROR 로그를 한 번 남기고 응답에는 원인을 노출하지 않는다")
    void handleBusinessException_serverError_logsCauseOnce() {
        IOException cause = new IOException("Internal filesystem detail");
        var response = handler.handleBusinessException(
                new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지를 읽지 못했습니다.", cause));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().error().message()).isEqualTo("이미지를 읽지 못했습니다.");
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy().getCause().getClassName()).isEqualTo(IOException.class.getName());
        assertThat(event.getThrowableProxy().getCause().getMessage()).isEqualTo(cause.getMessage());
    }

    @Test
    @DisplayName("4xx 업무 예외는 원인 스택 없이 WARN 로그만 한 번 남긴다")
    void handleBusinessException_clientError_doesNotLogCause() {
        var response = handler.handleBusinessException(new BusinessException(ErrorCode.INVALID_INPUT,
                "잘못된 이미지입니다.", new IOException("Internal decoder detail")));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().message()).isEqualTo("잘못된 이미지입니다.");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
    }
}
