package com.ucamp.app.domain.shop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ucamp.app.common.auth.AuthUser;
import com.ucamp.app.common.auth.UserRole;
import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;
import com.ucamp.app.common.lock.ShopLock;
import com.ucamp.app.common.response.PageResponse;
import com.ucamp.app.common.util.TimeUtils;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSegmentFilter;
import com.ucamp.app.domain.shop.dto.request.ShopCustomerSort;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerDetailResponse;
import com.ucamp.app.domain.shop.dto.response.ShopCustomerResponse;
import com.ucamp.app.domain.shop.entity.ShopCustomerMemo;
import com.ucamp.app.domain.shop.policy.CustomerSegment;
import com.ucamp.app.domain.shop.repository.ShopCustomerMemoRepository;
import com.ucamp.app.domain.shop.repository.ShopCustomerRepository;

class ShopCustomerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 14, 0);
    private static final AuthUser OWNER = new AuthUser(11L, UserRole.OWNER, 1L);

    private ShopCustomerRepository shopCustomerRepository;
    private ShopCustomerMemoRepository shopCustomerMemoRepository;
    private ShopLock shopLock;
    private ShopCustomerService shopCustomerService;

    @BeforeEach
    void setUp() {
        shopCustomerRepository = mock(ShopCustomerRepository.class);
        shopCustomerMemoRepository = mock(ShopCustomerMemoRepository.class);
        shopLock = mock(ShopLock.class);
        Clock clock = Clock.fixed(NOW.atZone(TimeUtils.KST).toInstant(), TimeUtils.KST);
        shopCustomerService = new ShopCustomerService(shopCustomerRepository, shopCustomerMemoRepository, shopLock,
                clock);
    }

    @Test
    @DisplayName("목록은 검색어의 LIKE 특수문자를 글자 그대로 찾고, 이용 횟수로 신규·재방문을 붙인다")
    void getCustomers_escapesKeywordAndSegments() {
        given(shopCustomerRepository.findCustomers(eq(1L), eq(NOW), anyString(), eq("RETURNING"),
                eq(CustomerSegment.RETURNING_MIN_VISITS), eq("VISITS"), eq(20L), eq(10)))
                .willReturn(List.of(customer(106L, 7L), customer(107L, 1L)));
        given(shopCustomerRepository.countCustomers(eq(1L), eq(NOW), anyString(), eq("RETURNING"), anyInt()))
                .willReturn(22L);

        PageResponse<ShopCustomerResponse> page = shopCustomerService.getCustomers(OWNER,
                ShopCustomerSegmentFilter.RETURNING, ShopCustomerSort.VISITS, " 50%_off ", 2, 10);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(shopCustomerRepository).findCustomers(eq(1L), eq(NOW), pattern.capture(), any(), anyInt(), any(),
                anyLong(), anyInt());
        assertThat(pattern.getValue()).isEqualTo("%50\\%\\_off%");
        assertThat(page.totalElements()).isEqualTo(22);
        assertThat(page.content()).extracting(ShopCustomerResponse::segment)
                .containsExactly(CustomerSegment.RETURNING, CustomerSegment.NEW);
        assertThat(page.content().get(0).hasMemo()).isTrue();
        // 목록은 연락처 가운데를 가린다
        assertThat(page.content().get(0).phone()).isEqualTo("010-****-0106");
    }

    @Test
    @DisplayName("연락처 가리기: 하이픈 형식은 가운데, 아니면 앞 3자리·뒤 4자리만 남긴다")
    void maskPhone_formats() {
        assertThat(ShopCustomerService.maskPhone("010-6666-0106")).isEqualTo("010-****-0106");
        assertThat(ShopCustomerService.maskPhone("02-123-4567")).isEqualTo("02-***-4567");
        assertThat(ShopCustomerService.maskPhone("01066660106")).isEqualTo("010****0106");
        assertThat(ShopCustomerService.maskPhone("1234")).isEqualTo("****");
        assertThat(ShopCustomerService.maskPhone(null)).isNull();
    }

    @Test
    @DisplayName("page·size 가 범위를 벗어나면 INVALID_INPUT 이다")
    void getCustomers_invalidPage_throws() {
        assertThatThrownBy(() -> shopCustomerService.getCustomers(OWNER, null, null, null, -1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> shopCustomerService.getCustomers(OWNER, null, null, null, 0, 101))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("이 매장 예약이 없는 고객의 상세는 404 이다")
    void getCustomer_notCustomer_throwsNotFound() {
        given(shopCustomerRepository.findCustomer(1L, 999L, NOW)).willReturn(Optional.empty());

        assertThatThrownBy(() -> shopCustomerService.getCustomer(OWNER, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("상세 이력은 취소·예정·이용 완료로 나눈다")
    void getCustomer_historyStatuses() {
        given(shopCustomerRepository.findCustomer(1L, 106L, NOW)).willReturn(Optional.of(customer(106L, 1L)));
        given(shopCustomerRepository.findHistory(1L, 106L)).willReturn(List.of(
                history(3L, NOW.plusDays(1), "CONFIRMED", null),
                history(2L, NOW.minusDays(1), "CANCELLED", "CUSTOMER"),
                history(1L, NOW.minusHours(2), "CONFIRMED", null),
                history(0L, NOW.minusDays(7), "NO_SHOW", null)));

        ShopCustomerDetailResponse detail = shopCustomerService.getCustomer(OWNER, 106L);

        // 상세는 연락처 전체
        assertThat(detail.customer().phone()).isEqualTo("010-6666-0106");
        assertThat(detail.memo()).isNull();
        assertThat(detail.reservations()).extracting(ShopCustomerDetailResponse.History::status).containsExactly(
                ShopCustomerDetailResponse.HistoryStatus.UPCOMING,
                ShopCustomerDetailResponse.HistoryStatus.CANCELLED,
                ShopCustomerDetailResponse.HistoryStatus.COMPLETED,
                ShopCustomerDetailResponse.HistoryStatus.NO_SHOW);
    }

    @Test
    @DisplayName("메모가 없으면 새로 만들고, 있으면 고치며, 앞뒤 공백은 지운다")
    void saveMemo_createsOrUpdates() {
        given(shopCustomerRepository.isCustomer(1L, 106L)).willReturn(true);
        given(shopCustomerMemoRepository.findByShopIdAndUserId(1L, 106L)).willReturn(Optional.empty());
        given(shopCustomerMemoRepository.save(any(ShopCustomerMemo.class))).willAnswer(call -> call.getArgument(0));

        assertThat(shopCustomerService.saveMemo(OWNER, 106L, "  누드톤 선호 ").memo()).isEqualTo("누드톤 선호");
        verify(shopLock).lock(1L);

        ShopCustomerMemo existing = ShopCustomerMemo.create(1L, 106L, "예전 메모");
        given(shopCustomerMemoRepository.findByShopIdAndUserId(1L, 106L)).willReturn(Optional.of(existing));

        assertThat(shopCustomerService.saveMemo(OWNER, 106L, "새 메모").memo()).isEqualTo("새 메모");
        assertThat(existing.getMemo()).isEqualTo("새 메모");
    }

    @Test
    @DisplayName("빈 메모는 지우고 null 을 돌려준다")
    void saveMemo_blank_deletes() {
        ShopCustomerMemo existing = ShopCustomerMemo.create(1L, 106L, "예전 메모");
        given(shopCustomerRepository.isCustomer(1L, 106L)).willReturn(true);
        given(shopCustomerMemoRepository.findByShopIdAndUserId(1L, 106L)).willReturn(Optional.of(existing));

        assertThat(shopCustomerService.saveMemo(OWNER, 106L, "   ")).isNull();
        verify(shopCustomerMemoRepository).delete(existing);
        verify(shopCustomerMemoRepository, never()).save(any());
    }

    @Test
    @DisplayName("이 매장 예약이 없는 고객에게는 메모를 남길 수 없다 (404)")
    void saveMemo_notCustomer_throwsNotFound() {
        given(shopCustomerRepository.isCustomer(1L, 999L)).willReturn(false);

        assertThatThrownBy(() -> shopCustomerService.saveMemo(OWNER, 999L, "메모"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(shopLock, shopCustomerMemoRepository);
    }

    @Test
    @DisplayName("매장이 아니면 FORBIDDEN 이다")
    void customer_throwsForbidden() {
        AuthUser customer = new AuthUser(1L, UserRole.CUSTOMER, null);

        assertThatThrownBy(() -> shopCustomerService.getCustomers(customer, null, null, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> shopCustomerService.saveMemo(customer, 1L, "메모"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(shopCustomerRepository, shopCustomerMemoRepository, shopLock);
    }

    private static ShopCustomerRepository.CustomerRow customer(Long userId, Long visitCount) {
        return new ShopCustomerRepository.CustomerRow() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getName() {
                return "김하은";
            }

            @Override
            public String getPhone() {
                return "010-6666-0106";
            }

            @Override
            public Long getVisitCount() {
                return visitCount;
            }

            @Override
            public Long getTotalAmount() {
                return visitCount * 30000;
            }

            @Override
            public LocalDateTime getFirstVisitAt() {
                return NOW.minusDays(30);
            }

            @Override
            public LocalDateTime getLastVisitAt() {
                return NOW.minusDays(3);
            }

            @Override
            public LocalDateTime getNextReservationAt() {
                return null;
            }

            @Override
            public Long getNoShowCount() {
                return 0L;
            }

            @Override
            public Long getHasMemo() {
                return visitCount > 1 ? 1L : 0L;
            }
        };
    }

    private static ShopCustomerRepository.HistoryRow history(Long id, LocalDateTime startsAt, String status,
            String cancelType) {
        return new ShopCustomerRepository.HistoryRow() {
            @Override
            public Long getReservationId() {
                return id;
            }

            @Override
            public LocalDateTime getStartsAt() {
                return startsAt;
            }

            @Override
            public LocalDateTime getEndsAt() {
                return startsAt.plusHours(1);
            }

            @Override
            public String getTreatmentName() {
                return "젤네일 원컬러";
            }

            @Override
            public Long getAppliedPrice() {
                return 34000L;
            }

            @Override
            public String getStatus() {
                return "NO_SHOW".equals(status) ? "CONFIRMED" : status;
            }

            @Override
            public String getCancelType() {
                return cancelType;
            }

            @Override
            public String getCustomerRequest() {
                return null;
            }

            @Override
            public Long getNoShow() {
                return "NO_SHOW".equals(status) ? 1L : 0L;
            }
        };
    }
}
