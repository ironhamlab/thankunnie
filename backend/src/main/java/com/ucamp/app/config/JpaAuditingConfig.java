package com.ucamp.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity 의 createdAt, updatedAt 자동 기록을 활성화한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
