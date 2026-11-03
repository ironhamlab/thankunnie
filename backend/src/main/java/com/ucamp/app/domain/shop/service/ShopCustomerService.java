package com.ucamp.app.domain.shop.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.cancel.entity.CancelType;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSegmentFilter;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSort;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerDetailResponse;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerMemoResponse;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerResponse;
import com.ucamp.app.domain.shop.entity.ShopCustomerMemo;
import com.ucamp.app.domain.shop.policy.CustomerSegment;
import com.ucamp.app.domain.shop.repository.ShopCustomerMemoRepository;
import com.ucamp.app.domain.shop.repository.ShopCustomerRepository;

/**
 * 사장님 고객 관리. 이 매장에 확정·취소 완료 예약이 있는 고객만 다루고, 다른 고객은 404 로 숨긴다.
 * 이용 횟수·금액은 조회할 때마다 예약에서 계산한다 (종료 시각이 지난 확정 예약 = 이용 추정).
 * 연락처는 여러 고객이 한 화면에 보이는 목록에서 가운데를 가리고, 고객 상세에서만 전체를 준다 (예약 상세와 같은 기준).
 */
@Service
@Transactional(readOnly = true)
public class ShopCustomerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ShopCustomerRepository shopCustomerRepository;
    private final ShopCustomerMemoRepository shopCustomerMemoRepository;
    private final ShopLock shopLock;
    private final Clock clock;

    public ShopCustomerService(ShopCustomerRepository shopCustomerRepository,
            ShopCustomerMemoRepository shopCustomerMemoRepository, ShopLock shopLock, Clock clock) {
        this.shopCustomerRepository = shopCustomerRepository;
        this.shopCustomerMemoRepository = shopCustomerMemoRepository;
        this.shopLock = shopLock;
        this.clock = clock;
    }

    public PageResponse<ShopCustomerResponse> getCustomers(AuthUser loginUser, ShopCustomerSegmentFilter segment,
            ShopCustomerSort sort, String keyword, int page, int size) {
        loginUser.requireRole(UserRole.OWNER);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page 는 0 이상, size 는 1~100 이어야 합니다.");
        }
        Long shopId = loginUser.shopId();
        LocalDateTime now = LocalDateTime.now(clock);
        String pattern = toLikePattern(keyword);
        String segmentName = (segment == null ? ShopCustomerSegmentFilter.ALL : segment).name();
        String sortName = (sort == null ? ShopCustomerSort.RECENT : sort).name();
        // 큰 page 에서 int 곱셈이 넘치지 않게 long 으로 계산한다
        long offset = (long) page * size;

        List<ShopCustomerResponse> content = shopCustomerRepository.findCustomers(shopId, now, pattern, segmentName,
                        CustomerSegment.RETURNING_MIN_VISITS, sortName, offset, size).stream()
                .map(row -> toResponse(row, true))
                .toList();
        long total = shopCustomerRepository.countCustomers(shopId, now, pattern, segmentName,
                CustomerSegment.RETURNING_MIN_VISITS);
        return PageResponse.of(content, page, size, total);
    }

    public ShopCustomerDetailResponse getCustomer(AuthUser loginUser, Long userId) {
        loginUser.requireRole(UserRole.OWNER);
        Long shopId = loginUser.shopId();
        LocalDateTime now = LocalDateTime.now(clock);
        ShopCustomerResponse customer = shopCustomerRepository.findCustomer(shopId, userId, now)
                .map(row -> toResponse(row, false))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ShopCustomerMemoResponse memo = shopCustomerMemoRepository.findByShopIdAndUserId(shopId, userId)
                .map(ShopCustomerService::toMemoResponse)
                .orElse(null);
        List<ShopCustomerDetailResponse.History> history = shopCustomerRepository.findHistory(shopId, userId)
                .stream()
                .map(row -> toHistory(row, now))
                .toList();
        return new ShopCustomerDetailResponse(customer, memo, history);
    }

    /**
     * 메모를 저장한다. 비우거나 공백만 보내면 지우고 null 을 돌려준다.
     * 같은 고객 메모의 첫 저장이 동시에 들어와도 한 행만 생기도록 매장 잠금으로 줄 세운다.
     */
    @Transactional
    public ShopCustomerMemoResponse saveMemo(AuthUser loginUser, Long userId, String memo) {
        loginUser.requireRole(UserRole.OWNER);
        Long shopId = loginUser.shopId();
        if (!shopCustomerRepository.isCustomer(shopId, userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        shopLock.lock(shopId);
        String text = memo == null ? "" : memo.strip();
        ShopCustomerMemo existing = shopCustomerMemoRepository.findByShopIdAndUserId(shopId, userId).orElse(null);
        if (text.isEmpty()) {
            if (existing != null) {
                shopCustomerMemoRepository.delete(existing);
            }
            return null;
        }
        if (existing == null) {
            existing = shopCustomerMemoRepository.save(ShopCustomerMemo.create(shopId, userId, text));
        } else {
            existing.changeMemo(text);
        }
        // 수정 시각이 응답에 담기도록 바로 반영한다
        shopCustomerMemoRepository.flush();
        return toMemoResponse(existing);
    }

    // 이름 일부 또는 연락처 일부. LIKE 특수문자는 글자 그대로 찾는다
    private static String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "%";
        }
        String escaped = keyword.strip()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static ShopCustomerResponse toResponse(ShopCustomerRepository.CustomerRow row, boolean maskPhone) {
        long visitCount = row.getVisitCount() == null ? 0 : row.getVisitCount();
        return new ShopCustomerResponse(
                row.getUserId(),
                row.getName(),
                maskPhone ? maskPhone(row.getPhone()) : row.getPhone(),
                visitCount,
                row.getTotalAmount() == null ? 0 : row.getTotalAmount(),
                toOffset(row.getFirstVisitAt()),
                toOffset(row.getLastVisitAt()),
                toOffset(row.getNextReservationAt()),
                CustomerSegment.of(visitCount),
                row.getHasMemo() != null && row.getHasMemo() == 1);
    }

    /**
     * 연락처 가운데를 가린다. 010-6666-0106 → 010-****-0106. 하이픈이 없으면 앞 3자리·뒤 4자리만 남긴다.
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String[] parts = phone.split("-");
        if (parts.length == 3) {
            return parts[0] + "-" + "*".repeat(parts[1].length()) + "-" + parts[2];
        }
        if (phone.length() <= 7) {
            return "*".repeat(phone.length());
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 7) + phone.substring(phone.length() - 4);
    }

    private static ShopCustomerDetailResponse.History toHistory(ShopCustomerRepository.HistoryRow row,
            LocalDateTime now) {
        ShopCustomerDetailResponse.HistoryStatus status;
        if ("CANCELLED".equals(row.getStatus())) {
            status = ShopCustomerDetailResponse.HistoryStatus.CANCELLED;
        } else if (row.getEndsAt().isAfter(now)) {
            status = ShopCustomerDetailResponse.HistoryStatus.UPCOMING;
        } else {
            status = ShopCustomerDetailResponse.HistoryStatus.COMPLETED;
        }
        return new ShopCustomerDetailResponse.History(
                row.getReservationId(),
                toOffset(row.getStartsAt()),
                toOffset(row.getEndsAt()),
                row.getTreatmentName(),
                row.getAppliedPrice() == null ? 0 : row.getAppliedPrice(),
                status,
                status == ShopCustomerDetailResponse.HistoryStatus.CANCELLED && row.getCancelType() != null
                        ? CancelType.valueOf(row.getCancelType()) : null,
                row.getCustomerRequest());
    }

    private static ShopCustomerMemoResponse toMemoResponse(ShopCustomerMemo memo) {
        return new ShopCustomerMemoResponse(memo.getMemo(), toOffset(memo.getUpdatedAt()));
    }

    private static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : TimeUtils.toOffset(value);
    }
}
