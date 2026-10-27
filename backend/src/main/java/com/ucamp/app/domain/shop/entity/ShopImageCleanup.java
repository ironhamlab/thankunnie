package com.ucamp.app.domain.shop.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;

/** 삭제 실패한 이미지의 영속 재시도 작업. 매장이 삭제되어도 정리할 수 있도록 FK를 두지 않는다. */
@Entity
@Table(name = "SHOP_IMAGE_CLEANUP")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopImageCleanup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_shop_image_cleanup")
    @SequenceGenerator(name = "seq_shop_image_cleanup", sequenceName = "SEQ_SHOP_IMAGE_CLEANUP", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "CLEANUP_ID", nullable = false)
    private Long cleanupId;

    @Column(name = "STORAGE_SCOPE", nullable = false, length = 512)
    private String storageScope;

    @Column(name = "FILE_NAME", nullable = false, length = 40)
    private String fileName;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private int attemptCount;

    @Column(name = "NEXT_ATTEMPT_AT", nullable = false)
    private LocalDateTime nextAttemptAt;

    /** 첫 재시도는 1분 뒤 실행한다. scope에는 키 등 자격 증명을 넣지 않는다. */
    public ShopImageCleanup(String storageScope, String fileName, LocalDateTime now) {
        this.storageScope = storageScope;
        this.fileName = fileName;
        this.nextAttemptAt = now.plusMinutes(1);
    }

    /** 실패할 때마다 대기 간격을 늘리되 최대 1시간마다 계속 재시도한다. */
    public void reschedule(LocalDateTime now) {
        attemptCount++;
        long minutes = Math.min(60L, 1L << Math.min(attemptCount, 6));
        nextAttemptAt = now.plusMinutes(minutes);
    }
}
