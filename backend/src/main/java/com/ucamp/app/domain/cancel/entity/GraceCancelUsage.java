package com.ucamp.app.domain.cancel.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.springframework.data.domain.Persistable;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;

/**
 * 무료 취소 사용 기록. 회원당 한국 날짜 기준 하루 1건이며, 두 번째 저장은 PK 중복으로 막힌다 (CNL-08).
 * 기본 정책보다 더 환불받는 경우에만 기록한다 (CNL-09).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "GRACE_CANCEL_USAGE")
@IdClass(GraceCancelUsageId.class)
public class GraceCancelUsage extends BaseTimeEntity implements Persistable<GraceCancelUsageId> {

    @Id
    @Column(name = "USER_ID")
    private Long userId;

    /** 한국 날짜. Oracle DATE 컬럼에 시각 00:00:00 으로 저장된다 */
    @Id
    @Column(name = "USED_DATE")
    private LocalDate usedDate;

    @Column(name = "CANCEL_ID", nullable = false)
    private Long cancelId;

    @Builder
    private GraceCancelUsage(Long userId, LocalDate usedDate, Long cancelId) {
        this.userId = userId;
        this.usedDate = usedDate;
        this.cancelId = cancelId;
    }

    @Override
    public GraceCancelUsageId getId() {
        return new GraceCancelUsageId(userId, usedDate);
    }

    /**
     * ID 를 직접 넣는 엔티티라 save() 가 merge 로 동작하면 같은 날 두 번째 기록이 기존 행을 덮어쓴다.
     * 저장 전(생성 시각 없음)이면 새 엔티티로 보고 insert 하게 해서 PK 중복이 그대로 드러나게 한다.
     */
    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}
