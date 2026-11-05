package com.ucamp.app.domain.notification.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;
import com.ucamp.app.domain.vacancy.service.AvailabilityService;

/**
 * 업장 알림에 고른 시술을, 빈자리 시작 시각에 예약할 수 있는지 본다.
 * 매장 알림 대상을 고를 때와, 문자를 보내기 직전에 같은 기준으로 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreSubscriptionTreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final AvailabilityService availabilityService;

    /**
     * 그 매장에서 판매 중인 시술이고, 빈자리 시작 시각에 소요 시간만큼 예약할 수 있으면 true.
     * 시술이 없거나 판매 중이 아니면 false.
     *
     * @param shopId       업장 알림의 매장
     * @param treatmentId  고객이 고른 시술
     * @param slotStartsAt 빈자리 시작 시각
     * @return 그 시각에 그 시술을 예약할 수 있으면 true
     */
    public boolean canBook(Long shopId, Long treatmentId, LocalDateTime slotStartsAt) {
        if (shopId == null || treatmentId == null || slotStartsAt == null) {
            return false;
        }
        Treatment treatment = treatmentRepository.findByTreatmentIdAndShopShopId(treatmentId, shopId).orElse(null);
        if (treatment == null || treatment.getStatus() != TreatmentStatus.ACTIVE) {
            return false;
        }
        // 소요 시간이 없으면 예약 구간을 만들 수 없다.
        Integer durationMinutes = treatment.getDurationMinutes();
        if (durationMinutes == null || durationMinutes <= 0) {
            return false;
        }
        return availabilityService.isAvailable(shopId, slotStartsAt, durationMinutes);
    }
}
