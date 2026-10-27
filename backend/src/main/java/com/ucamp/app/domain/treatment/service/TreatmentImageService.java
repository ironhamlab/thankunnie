package com.ucamp.app.domain.treatment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.dto.response.ShopImageContentResponse;
import com.ucamp.app.domain.shop.repository.ShopRepository;
import com.ucamp.app.domain.shop.service.ShopImageStorage;
import com.ucamp.app.domain.treatment.dto.response.TreatmentImageResponse;
import com.ucamp.app.domain.treatment.entity.Treatment;
import com.ucamp.app.domain.treatment.repository.TreatmentRepository;

/** 시술 대표 이미지의 권한 검사와 DB·파일 변경 순서를 관리한다. */
@Service
@Transactional(readOnly = true)
public class TreatmentImageService {

    private final ShopRepository shopRepository;
    private final ShopImageStorage storage;
    private final TreatmentRepository treatmentRepository;

    public TreatmentImageService(ShopRepository shopRepository, ShopImageStorage storage,
            TreatmentRepository treatmentRepository) {
        this.shopRepository = shopRepository;
        this.storage = storage;
        this.treatmentRepository = treatmentRepository;
    }

    /** 새 파일 저장이 성공한 경우에만 URL을 바꾸고 기존 파일은 DB 커밋 후 삭제한다. */
    @Transactional
    public TreatmentImageResponse uploadImage(AuthUser user, Long shopId, Long treatmentId, MultipartFile file) {
        user.requireShopOwner(shopId);
        lockShop(shopId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        String previousUrl = treatment.getImageUrl();
        String fileName = storage.save(file);
        synchronizeFiles(shopId, treatmentId, previousUrl, fileName);
        String imageUrl = imagePrefix(shopId, treatmentId) + fileName;
        treatment.changeImage(imageUrl);
        return new TreatmentImageResponse(treatmentId, imageUrl);
    }

    /** DB 삭제가 확정된 경우에만 파일을 정리한다. 이미지가 없으면 그대로 성공한다. */
    @Transactional
    public void deleteImage(AuthUser user, Long shopId, Long treatmentId) {
        user.requireShopOwner(shopId);
        lockShop(shopId);
        Treatment treatment = findTreatment(shopId, treatmentId);
        synchronizeFiles(shopId, treatmentId, treatment.getImageUrl(), null);
        treatment.changeImage(null);
    }

    /** 현재 대표 이미지만 공개 조회한다. 교체·삭제된 URL은 404를 반환한다. */
    public ShopImageContentResponse getImage(Long shopId, Long treatmentId, String fileName) {
        if (!storage.isStoredFileName(fileName)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Treatment treatment = findTreatment(shopId, treatmentId);
        if (!(imagePrefix(shopId, treatmentId) + fileName).equals(treatment.getImageUrl())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return storage.read(fileName);
    }

    private void lockShop(Long shopId) {
        shopRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Treatment findTreatment(Long shopId, Long treatmentId) {
        return treatmentRepository.findByTreatmentIdAndShopShopId(treatmentId, shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void synchronizeFiles(Long shopId, Long treatmentId, String previousUrl, String newFileName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String prefix = imagePrefix(shopId, treatmentId);
                // 기존 외부 URL이나 다른 매장 경로는 파일 삭제 대상으로 해석하지 않는다.
                if (previousUrl != null && previousUrl.startsWith(prefix)) {
                    storage.deleteQuietly(previousUrl.substring(prefix.length()));
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.deleteQuietly(newFileName);
                }
            }
        });
    }

    private String imagePrefix(Long shopId, Long treatmentId) {
        return "/api/v1/shops/" + shopId + "/treatments/" + treatmentId + "/images/";
    }
}
