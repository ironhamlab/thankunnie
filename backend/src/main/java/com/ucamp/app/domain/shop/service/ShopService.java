package com.ucamp.app.domain.shop.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.dto.request.ShopUpdateRequest;
import com.ucamp.app.domain.shop.dto.response.ShopDetailResponse;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.repository.ShopRepository;
import com.ucamp.app.domain.shop.repository.ShopCategoryRepository;

/** 본인 매장 조회 및 프로필 저장. */
@Service
@Transactional(readOnly = true)
public class ShopService {

    private final ShopRepository shopRepository;
    private final ShopCategoryRepository categoryRepository;

    public ShopService(ShopRepository shopRepository, ShopCategoryRepository categoryRepository) {
        this.shopRepository = shopRepository;
        this.categoryRepository = categoryRepository;
    }

    /** 본인 매장의 프로필을 조회한다. */
    public ShopDetailResponse getShop(AuthUser user, Long shopId) {
        user.requireShopOwner(shopId);
        return ShopDetailResponse.from(findShop(shopId));
    }

    /** 계정 정보와 상태를 유지하면서 본인 매장 프로필을 저장한다. */
    @Transactional
    public ShopDetailResponse updateShop(AuthUser user, Long shopId, ShopUpdateRequest request) {
        user.requireShopOwner(shopId);
        // 대표 이미지 교체와 동시에 수정해도 이전 이미지 URL로 덮어쓰지 않도록 같은 행 잠금을 사용한다.
        Shop shop = shopRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "존재하지 않는 매장 카테고리입니다.");
        }
        shop.updateProfile(request);
        return ShopDetailResponse.from(shop);
    }

    private Shop findShop(Long shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
