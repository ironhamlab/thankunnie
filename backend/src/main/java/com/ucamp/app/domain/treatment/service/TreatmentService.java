package com.ucamp.app.domain.treatment.service;

import java.util.List;

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

    public TreatmentService(TreatmentRepository treatmentRepository, ShopRepository shopRepository,
            TreatmentImageService treatmentImageService) {
        this.treatmentRepository = treatmentRepository;
        this.shopRepository = shopRepository;
        this.treatmentImageService = treatmentImageService;
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
        return treatments.stream()
                .map(TreatmentDetailResponse::from).toList();
    }

    public TreatmentDetailResponse getTreatment(AuthUser user, Long shopId, Long treatmentId) {
        user.requireShopOwner(shopId);
        return TreatmentDetailResponse.from(findTreatment(shopId, treatmentId));
    }

    @Transactional
    public TreatmentDetailResponse createTreatment(AuthUser user, Long shopId, TreatmentSaveRequest request) {
        user.requireShopOwner(shopId);
        var shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Treatment treatment = Treatment.create(shop, request);
        return TreatmentDetailResponse.from(treatmentRepository.saveAndFlush(treatment));
    }

    @Transactional
    public TreatmentDetailResponse updateTreatment(AuthUser user, Long shopId, Long treatmentId,
            TreatmentSaveRequest request) {
        user.requireShopOwner(shopId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.updateMenu(request);
        treatmentRepository.flush();
        return TreatmentDetailResponse.from(treatment);
    }

    @Transactional
    public TreatmentDetailResponse updateStatus(AuthUser user, Long shopId, Long treatmentId,
            TreatmentStatusUpdateRequest request) {
        user.requireShopOwner(shopId);
        // 삭제는 전용 API로만 처리해 대표 이미지 정리가 빠지지 않게 한다.
        rejectDeletedStatus(request.status());
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.changeStatus(request.status());
        treatmentRepository.flush();
        return TreatmentDetailResponse.from(treatment);
    }

    /** 기존 예약이 시술을 참조하므로 행은 남기고 DELETED로 바꾼 뒤 대표 이미지를 정리한다. */
    @Transactional
    public void deleteTreatment(AuthUser user, Long shopId, Long treatmentId) {
        user.requireShopOwner(shopId);
        treatmentImageService.deleteImage(user, shopId, treatmentId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.changeStatus(TreatmentStatus.DELETED);
        treatmentRepository.flush();
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
