package com.ucamp.app.domain.treatment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.repository.ShopRepository;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.dto.request.TreatmentStatusUpdateRequest;
import com.ucamp.app.domain.treatment.dto.response.TreatmentDetailResponse;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;

@Service
@Transactional(readOnly = true)
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final ShopRepository shopRepository;
    private final TreatmentImageService treatmentImageService;
    private final Clock clock;

    public TreatmentService(TreatmentRepository treatmentRepository, ShopRepository shopRepository,
            TreatmentImageService treatmentImageService, Clock clock) {
        this.treatmentRepository = treatmentRepository;
        this.shopRepository = shopRepository;
        this.treatmentImageService = treatmentImageService;
        this.clock = clock;
    }

    public List<TreatmentDetailResponse> getTreatments(AuthUser user, Long shopId) {
        return getTreatments(user, shopId, null);
    }

    /** 관리 목록은 전체 상태를, 신규 빈자리 선택은 ACTIVE 상태를 조회한다. */
    public List<TreatmentDetailResponse> getTreatments(AuthUser user, Long shopId, TreatmentStatus status) {
        user.requireShopOwner(shopId);
        requireShop(shopId);
        rejectDeletedStatus(status);
        List<Treatment> treatments = status == null
                ? treatmentRepository.findByShopShopIdAndStatusNotOrderByTreatmentIdDesc(shopId,
                        TreatmentStatus.DELETED)
                : treatmentRepository.findByShopShopIdAndStatusOrderByTreatmentIdDesc(shopId, status);
        Map<Long, Long> remaining = remainingReservationCounts(shopId);
        return treatments.stream()
                .map(treatment -> TreatmentDetailResponse.from(treatment,
                        remaining.getOrDefault(treatment.getTreatmentId(), 0L)))
                .toList();
    }

    public TreatmentDetailResponse getTreatment(AuthUser user, Long shopId, Long treatmentId) {
        user.requireShopOwner(shopId);
        return toResponse(shopId, findTreatment(shopId, treatmentId));
    }

    @Transactional
    public TreatmentDetailResponse createTreatment(AuthUser user, Long shopId, TreatmentSaveRequest request) {
        user.requireShopOwner(shopId);
        var shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Treatment treatment = Treatment.create(shop, request);
        return TreatmentDetailResponse.from(treatmentRepository.saveAndFlush(treatment), 0L);
    }

    @Transactional
    public TreatmentDetailResponse updateTreatment(AuthUser user, Long shopId, Long treatmentId,
            TreatmentSaveRequest request) {
        user.requireShopOwner(shopId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.updateMenu(request);
        treatmentRepository.flush();
        return toResponse(shopId, treatment);
    }

    /** 비활성화는 새 예약만 막고 남은 예약은 그대로 진행되므로 예약이 있어도 허용한다. */
    @Transactional
    public TreatmentDetailResponse updateStatus(AuthUser user, Long shopId, Long treatmentId,
            TreatmentStatusUpdateRequest request) {
        user.requireShopOwner(shopId);
        // 삭제는 전용 API로만 처리해 남은 예약 검사와 대표 이미지 정리가 빠지지 않게 한다.
        rejectDeletedStatus(request.status());
        // 예약 생성의 상태 재검사와 같은 매장 잠금으로 직렬화한다.
        shopRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.changeStatus(request.status());
        treatmentRepository.flush();
        return toResponse(shopId, treatment);
    }

    /**
     * 기존 예약이 시술을 참조하므로 행은 남기고 DELETED로 바꾼 뒤 대표 이미지를 정리한다.
     * 남은 예약이 있으면 TREATMENT_HAS_RESERVATION으로 거절한다.
     */
    @Transactional
    public void deleteTreatment(AuthUser user, Long shopId, Long treatmentId) {
        user.requireShopOwner(shopId);
        findTreatment(shopId, treatmentId);
        // 예약 점유와 같은 SHOP 행 잠금으로 줄 세워, 검사를 통과한 뒤 이 시술에 새 예약이 생기지 않게 한다
        shopRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (remainingReservationCounts(shopId).getOrDefault(treatmentId, 0L) > 0) {
            throw new BusinessException(ErrorCode.TREATMENT_HAS_RESERVATION);
        }
        treatmentImageService.deleteImage(user, shopId, treatmentId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.changeStatus(TreatmentStatus.DELETED);
        treatmentRepository.flush();
    }

    private TreatmentDetailResponse toResponse(Long shopId, Treatment treatment) {
        return TreatmentDetailResponse.from(treatment,
                remainingReservationCounts(shopId).getOrDefault(treatment.getTreatmentId(), 0L));
    }

    private Map<Long, Long> remainingReservationCounts(Long shopId) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : treatmentRepository.countRemainingReservationsByShop(shopId,
                LocalDateTime.now(clock))) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Treatment findTreatment(Long shopId, Long treatmentId) {
        return treatmentRepository.findByTreatmentIdAndShopShopIdAndStatusNot(treatmentId, shopId,
                        TreatmentStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void rejectDeletedStatus(TreatmentStatus status) {
        if (status == TreatmentStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireShop(Long shopId) {
        if (!shopRepository.existsById(shopId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
