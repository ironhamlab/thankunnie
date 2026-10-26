package com.ucamp.app.domain.treatment.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.entity.ShopCategory;
import com.ucamp.app.domain.shop.repository.ShopCategoryRepository;
import com.ucamp.app.domain.shop.repository.ShopRepository;
import com.ucamp.app.domain.treatment.dto.request.TreatmentSaveRequest;
import com.ucamp.app.domain.treatment.dto.request.TreatmentStatusUpdateRequest;
import com.ucamp.app.domain.treatment.dto.response.TreatmentCategoryResponse;
import com.ucamp.app.domain.treatment.dto.response.TreatmentDetailResponse;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.entity.TreatmentStatus;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;

@Service
@Transactional(readOnly = true)
public class TreatmentService {

    private final TreatmentRepository treatmentRepository;
    private final ShopRepository shopRepository;
    private final ShopCategoryRepository categoryRepository;

    public TreatmentService(TreatmentRepository treatmentRepository, ShopRepository shopRepository,
            ShopCategoryRepository categoryRepository) {
        this.treatmentRepository = treatmentRepository;
        this.shopRepository = shopRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<TreatmentDetailResponse> getTreatments(AuthUser user, Long shopId) {
        return getTreatments(user, shopId, null);
    }

    /** 관리 목록은 전체 상태를, 신규 빈자리 선택은 ACTIVE 상태를 조회한다. */
    public List<TreatmentDetailResponse> getTreatments(AuthUser user, Long shopId, TreatmentStatus status) {
        user.requireShopOwner(shopId);
        requireShop(shopId);
        List<Treatment> treatments = status == null
                ? treatmentRepository.findByShopShopIdOrderByTreatmentIdDesc(shopId)
                : treatmentRepository.findByShopShopIdAndStatusOrderByTreatmentIdDesc(shopId, status);
        return treatments.stream()
                .map(TreatmentDetailResponse::from).toList();
    }

    public List<TreatmentCategoryResponse> getCategories(AuthUser user, Long shopId) {
        user.requireShopOwner(shopId);
        requireShop(shopId);
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(TreatmentCategoryResponse::from).toList();
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
        Treatment treatment = Treatment.create(shop, findCategory(request.categoryId()), request);
        return TreatmentDetailResponse.from(treatmentRepository.saveAndFlush(treatment));
    }

    @Transactional
    public TreatmentDetailResponse updateTreatment(AuthUser user, Long shopId, Long treatmentId,
            TreatmentSaveRequest request) {
        user.requireShopOwner(shopId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.updateMenu(findCategory(request.categoryId()), request);
        treatmentRepository.flush();
        return TreatmentDetailResponse.from(treatment);
    }

    @Transactional
    public TreatmentDetailResponse updateStatus(AuthUser user, Long shopId, Long treatmentId,
            TreatmentStatusUpdateRequest request) {
        user.requireShopOwner(shopId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        treatment.changeStatus(request.status());
        treatmentRepository.flush();
        return TreatmentDetailResponse.from(treatment);
    }

    private Treatment findTreatment(Long shopId, Long treatmentId) {
        return treatmentRepository.findByTreatmentIdAndShopShopId(treatmentId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ShopCategory findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 카테고리입니다."));
    }

    private void requireShop(Long shopId) {
        if (!shopRepository.existsById(shopId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
