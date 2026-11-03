package com.ucamp.app.domain.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.policy.BookingPolicy;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.domain.auth.dto.response.ShopApplicationResponse;
import com.ucamp.app.domain.auth.entity.ShopAccount;
import com.ucamp.app.domain.auth.entity.ShopAccountStatus;
import com.ucamp.app.domain.auth.repository.ShopAccountRepository;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.shop.entity.ShopCategory;
import com.ucamp.app.domain.shop.repository.ShopCategoryRepository;
import com.ucamp.app.domain.vacancy.policy.SalePolicy;

/**
 * 관리자의 매장 가입 신청 조회와 승인·거절, 잘못 처리한 신청 되돌리기.
 * 상태를 바꾸는 작업은 매장 행을 잠가 고객 예약 점유(ShopLock)와 줄을 세운다.
 */
@Service
@Transactional(readOnly = true)
public class AdminShopApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 50;
    // 먼저 신청한 매장부터 처리한다. 신청 시각이 같아도 페이지 사이 순서가 바뀌지 않게 ID 를 함께 본다
    private static final Sort OLDEST_FIRST = Sort.by("createdAt", "shopId");

    private final ShopAccountRepository shopAccountRepository;
    private final ShopCategoryRepository shopCategoryRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    public AdminShopApplicationService(ShopAccountRepository shopAccountRepository,
            ShopCategoryRepository shopCategoryRepository, ReservationRepository reservationRepository,
            Clock clock) {
        this.shopAccountRepository = shopAccountRepository;
        this.shopCategoryRepository = shopCategoryRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    /**
     * 상태별 가입 신청 목록을 신청 순으로 조회한다. 검색어가 있으면 매장명·아이디·연락처로 거른다.
     *
     * @param keyword 검색어. 없거나 비어 있으면 전체
     * @throws BusinessException 관리자가 아니면 FORBIDDEN, 페이지 범위나 검색어 길이가 틀리면 INVALID_INPUT
     */
    public PageResponse<ShopApplicationResponse> getApplications(AuthUser loginUser, ShopAccountStatus status,
            String keyword, int page, int size) {
        loginUser.requireRole(UserRole.ADMIN);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page 는 0 이상, size 는 1~100 이어야 합니다.");
        }
        Page<ShopAccount> shops = findShops(status, keyword, PageRequest.of(page, size, OLDEST_FIRST));
        Map<Long, String> categoryNames = shopCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(ShopCategory::getCategoryId, ShopCategory::getName));
        List<ShopApplicationResponse> content = shops.getContent().stream()
                .map(shop -> ShopApplicationResponse.of(shop, categoryNames.get(shop.getCategoryId())))
                .toList();
        return PageResponse.of(content, page, size, shops.getTotalElements());
    }

    /**
     * 가입을 승인한다.
     *
     * @throws BusinessException 관리자가 아니면 FORBIDDEN, 신청이 없으면 ADMIN_SHOP_APPLICATION_NOT_FOUND,
     *                           이미 처리했으면 ADMIN_SHOP_APPLICATION_NOT_PENDING
     */
    @Transactional
    public ShopApplicationResponse approve(AuthUser loginUser, Long shopId) {
        ShopAccount shop = findForUpdate(loginUser, shopId, ShopAccountStatus.PENDING,
                ErrorCode.ADMIN_SHOP_APPLICATION_NOT_PENDING);
        shop.approve();
        return toResponse(shop);
    }

    /**
     * 가입을 거절하고 사유를 남긴다.
     *
     * @throws BusinessException 관리자가 아니면 FORBIDDEN, 신청이 없으면 ADMIN_SHOP_APPLICATION_NOT_FOUND,
     *                           이미 처리했으면 ADMIN_SHOP_APPLICATION_NOT_PENDING
     */
    @Transactional
    public ShopApplicationResponse reject(AuthUser loginUser, Long shopId, String reason) {
        ShopAccount shop = findForUpdate(loginUser, shopId, ShopAccountStatus.PENDING,
                ErrorCode.ADMIN_SHOP_APPLICATION_NOT_PENDING);
        shop.reject(reason.trim());
        return toResponse(shop);
    }

    /**
     * 잘못 승인한 매장을 거절 상태로 바꾼다. 고객이 예약한 시술을 받지 못하게 되므로 진행 중이거나 앞으로 있을 예약이 있으면 막는다.
     *
     * @throws BusinessException 관리자가 아니면 FORBIDDEN, 신청이 없으면 ADMIN_SHOP_APPLICATION_NOT_FOUND,
     *                           승인된 매장이 아니면 ADMIN_SHOP_APPLICATION_NOT_APPROVED,
     *                           예약이 있으면 ADMIN_SHOP_APPLICATION_HAS_RESERVATION
     */
    @Transactional
    public ShopApplicationResponse cancelApproval(AuthUser loginUser, Long shopId, String reason) {
        ShopAccount shop = findForUpdate(loginUser, shopId, ShopAccountStatus.ACTIVE,
                ErrorCode.ADMIN_SHOP_APPLICATION_NOT_APPROVED);
        requireNoReservation(shopId);
        shop.reject(reason.trim());
        return toResponse(shop);
    }

    /**
     * 잘못 거절한 신청을 승인 대기로 되돌린다.
     *
     * @throws BusinessException 관리자가 아니면 FORBIDDEN, 신청이 없으면 ADMIN_SHOP_APPLICATION_NOT_FOUND,
     *                           거절된 신청이 아니면 ADMIN_SHOP_APPLICATION_NOT_REJECTED
     */
    @Transactional
    public ShopApplicationResponse cancelRejection(AuthUser loginUser, Long shopId) {
        ShopAccount shop = findForUpdate(loginUser, shopId, ShopAccountStatus.REJECTED,
                ErrorCode.ADMIN_SHOP_APPLICATION_NOT_REJECTED);
        shop.restoreToPending();
        return toResponse(shop);
    }

    private Page<ShopAccount> findShops(ShopAccountStatus status, String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return shopAccountRepository.findByStatus(status, pageable);
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "keyword 는 50자 이하여야 합니다.");
        }
        return shopAccountRepository.searchByStatus(status, likePattern(trimmed.toUpperCase()),
                likePattern(trimmed.replace("-", "")), pageable);
    }

    // 검색어의 % · _ 가 와일드카드로 해석되지 않게 한다. 이스케이프 문자는 쿼리의 escape '!' 와 같다
    private static String likePattern(String keyword) {
        return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private ShopAccount findForUpdate(AuthUser loginUser, Long shopId, ShopAccountStatus expected,
            ErrorCode wrongStatus) {
        loginUser.requireRole(UserRole.ADMIN);
        ShopAccount shop = shopAccountRepository.findByIdForUpdate(shopId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_SHOP_APPLICATION_NOT_FOUND));
        if (shop.getStatus() != expected) {
            throw new BusinessException(wrongStatus);
        }
        return shop;
    }

    // 예약은 오늘 포함 BOOKING_MAX_DAYS 안에서만 잡히므로 그 뒤까지 보면 앞으로 있을 예약이 모두 들어온다
    private void requireNoReservation(Long shopId) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean reserved = reservationRepository
                .findActiveOverlapping(shopId, now, now.plusDays(BookingPolicy.BOOKING_MAX_DAYS + 1L)).stream()
                .anyMatch(reservation -> SalePolicy.isOccupying(
                        reservation.getStatus(), reservation.getHoldExpiresAt(), now));
        if (reserved) {
            throw new BusinessException(ErrorCode.ADMIN_SHOP_APPLICATION_HAS_RESERVATION);
        }
    }

    private ShopApplicationResponse toResponse(ShopAccount shop) {
        String categoryName = shopCategoryRepository.findById(shop.getCategoryId())
                .map(ShopCategory::getName)
                .orElse(null);
        return ShopApplicationResponse.of(shop, categoryName);
    }
}
