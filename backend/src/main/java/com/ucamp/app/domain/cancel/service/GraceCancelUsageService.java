package com.ucamp.app.domain.cancel.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.cancel.entity.GraceCancelUsage;
import com.ucamp.app.domain.cancel.repository.GraceCancelUsageRepository;

/**
 * 무료 취소 사용 기록 (E-03). 사용 날짜는 RefundPolicy.graceUsageDate(now) 로 구해 넘긴다.
 */
@Service
@Transactional(readOnly = true)
public class GraceCancelUsageService {

    private final GraceCancelUsageRepository graceCancelUsageRepository;

    public GraceCancelUsageService(GraceCancelUsageRepository graceCancelUsageRepository) {
        this.graceCancelUsageRepository = graceCancelUsageRepository;
    }

    /**
     * 해당 날짜에 무료 취소를 이미 사용했는지. 견적 계산(RefundPolicy.quote)의 graceUsedToday 에 넘긴다.
     */
    public boolean isUsed(Long userId, LocalDate usedDate) {
        return graceCancelUsageRepository.existsByUserIdAndUsedDate(userId, usedDate);
    }

    /**
     * 무료 취소 사용을 기록한다. 견적 조회 후 다른 요청이 먼저 사용했으면 PK 중복이 나며 "오늘 이미 사용"으로 실패한다.
     * 이 예외로 취소 접수 트랜잭션 전체가 롤백되므로, 고객은 다시 조회한 견적(무료 취소 미적용)으로 재확인한다.
     */
    @Transactional
    public void record(Long userId, LocalDate usedDate, Long cancelId) {
        try {
            graceCancelUsageRepository.saveAndFlush(GraceCancelUsage.builder()
                    .userId(userId)
                    .usedDate(usedDate)
                    .cancelId(cancelId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CANCEL_GRACE_ALREADY_USED);
        }
    }

    /**
     * 결제 취소 실패 시 해당 취소의 무료 취소 기록을 지운다 (CNL-23). 기록이 없으면 아무것도 하지 않는다.
     * 벌크 삭제 + 영속성 컨텍스트 clear 를 쓰면 같은 트랜잭션에서 먼저 조회한 취소·예약 엔티티가 분리되어
     * 이후 변경(fail()·예약 상태 복구)이 반영되지 않으므로, 조회 후 엔티티 단위로 삭제한다.
     * 바로 flush 해서 같은 트랜잭션에서 같은 날 무료 취소를 다시 기록해도 키가 겹치지 않게 한다.
     */
    @Transactional
    public void release(Long cancelId) {
        List<GraceCancelUsage> usages = graceCancelUsageRepository.findAllByCancelId(cancelId);
        graceCancelUsageRepository.deleteAll(usages);
        graceCancelUsageRepository.flush();
    }
}
