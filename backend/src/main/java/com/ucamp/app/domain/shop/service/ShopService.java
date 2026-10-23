package com.ucamp.app.domain.shop.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.domain.shop.dto.request.ShopSaveRequest;
import com.ucamp.app.domain.shop.dto.response.ShopDetailResponse;
import com.ucamp.app.domain.shop.entity.Shop;
import com.ucamp.app.domain.shop.repository.ShopRepository;

/** 본인 매장 정보 관리. 모든 접근에서 인증된 매장 ID를 검사한다. */
@Service
@Transactional(readOnly = true)
public class ShopService {

    private final ShopRepository shopRepository;

    public ShopService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    /** 인증에서 할당된 본인 매장 정보를 최초 등록한다. */
    @Transactional
    public ShopDetailResponse createShop(AuthUser user, ShopSaveRequest request) {
        user.requireShopOwner(user.shopId());
        if (shopRepository.existsById(user.shopId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Shop shop = new Shop(user.shopId(), request.name(), request.description(), request.address(),
                request.addressDetail(), request.latitude(), request.longitude());
        return ShopDetailResponse.from(shopRepository.save(shop));
    }

    /** 본인 매장 정보를 조회한다. */
    public ShopDetailResponse getShop(AuthUser user, Long shopId) {
        user.requireShopOwner(shopId);
        return ShopDetailResponse.from(findShop(shopId));
    }

    /** 본인 매장 정보를 전체 수정한다. */
    @Transactional
    public ShopDetailResponse updateShop(AuthUser user, Long shopId, ShopSaveRequest request) {
        user.requireShopOwner(shopId);
        Shop shop = findShop(shopId);
        shop.updateInformation(request.name(), request.description(), request.address(), request.addressDetail(),
                request.latitude(), request.longitude());
        return ShopDetailResponse.from(shop);
    }

    private Shop findShop(Long shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
