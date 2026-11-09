package com.ucamp.app.domain.shop.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.dto.response.ShopImageContentResponse;
import com.ucamp.app.domain.shop.dto.response.ShopImageResponse;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.repository.ShopRepository;

/** 매장 대표 이미지의 권한 검사와 DB·파일 변경 순서를 관리한다. */
@Service
@Transactional(readOnly = true)
public class ShopImageService {

    private final ShopRepository shopRepository;
    private final ShopImageStorage storage;
    private final PlatformTransactionManager transactionManager;

    public ShopImageService(ShopRepository shopRepository, ShopImageStorage storage,
            PlatformTransactionManager transactionManager) {
        this.shopRepository = shopRepository;
        this.storage = storage;
        this.transactionManager = transactionManager;
    }

    /** 새 파일 저장이 성공한 경우에만 URL을 바꾸고 기존 파일은 DB 커밋 후 삭제한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ShopImageResponse uploadImage(AuthUser user, Long shopId, MultipartFile file) {
        user.requireShopOwner(shopId);
        if (!shopRepository.existsById(shopId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String fileName = storage.save(file);
        AtomicBoolean cleanupRegistered = new AtomicBoolean();
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                // 잠금 획득이나 재조회 실패도 새 파일 정리 대상에 포함한다.
                synchronizeFiles(shopId, null, fileName);
                cleanupRegistered.set(true);
                Shop shop = findForUpdate(shopId);
                synchronizeFiles(shopId, shop.getImageUrl(), null);
                String imageUrl = imagePrefix(shopId) + fileName;
                shop.changeImage(imageUrl);
                return new ShopImageResponse(shopId, imageUrl);
            });
        } catch (RuntimeException exception) {
            // 트랜잭션 시작 자체가 실패하면 롤백 콜백이 등록되지 않는다.
            if (!cleanupRegistered.get()) {
                storage.deleteQuietly(fileName);
            }
            throw exception;
        }
    }

    /** DB 삭제가 확정된 경우에만 파일을 정리한다. 이미지가 없으면 그대로 성공한다. */
    @Transactional
    public void deleteImage(AuthUser user, Long shopId) {
        user.requireShopOwner(shopId);
        Shop shop = findForUpdate(shopId);
        synchronizeFiles(shopId, shop.getImageUrl(), null);
        shop.changeImage(null);
    }

    /** 현재 대표 이미지만 공개 조회한다. 교체·삭제된 URL은 404를 반환한다. */
    public ShopImageContentResponse getImage(Long shopId, String fileName) {
        if (!storage.isStoredFileName(fileName)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!(imagePrefix(shopId) + fileName).equals(shop.getImageUrl())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return storage.read(fileName);
    }

    private Shop findForUpdate(Long shopId) {
        return shopRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void synchronizeFiles(Long shopId, String previousUrl, String newFileName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String prefix = imagePrefix(shopId);
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

    private String imagePrefix(Long shopId) {
        return "/api/v1/shops/" + shopId + "/images/";
    }
}
