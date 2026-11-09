package com.ucamp.app.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.reservation.entity.ReservationStatus;

// 초기 데이터(매장 1: 예약 1·3·5·6, 매장 2: 예약 2·4)로 조회 범위·정렬·결제·취소 결합을 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationQueryRepository.class, TimeConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ReservationQueryRepositoryTest {

    private static final Set<ReservationStatus> NOT_EXPIRED =
            EnumSet.complementOf(EnumSet.of(ReservationStatus.EXPIRED));

    @Autowired
    private ReservationQueryRepository reservationQueryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("탈퇴 회원의 예약은 휴대폰 번호를 보내지 않는다")
    void findOne_withdrawnCustomer_hidesPhone() {
        assertThat(reservationQueryRepository.findOne(3L, 1L, null).orElseThrow().customerPhone()).isNotNull();
        jdbcTemplate.update("UPDATE APP_USER SET STATUS = 'INACTIVE', NAME = '탈퇴회원' WHERE USER_ID = 1");

        ReservationView view = reservationQueryRepository.findOne(3L, 1L, null).orElseThrow();

        assertThat(view.customerName()).isEqualTo("탈퇴회원");
        assertThat(view.customerPhone()).isNull();
    }

    @Test
    @DisplayName("매장 범위로 조회하면 그 매장 예약만 시작 시각 순으로 나온다")
    void search_shopScope_returnsShopReservationsInStartOrder() {
        ReservationSearch search = new ReservationSearch(1L, null, NOT_EXPIRED, null, null);

        List<ReservationView> views = reservationQueryRepository.search(search, 0, 20);

        assertThat(views).extracting(ReservationView::reservationId).containsExactly(1L, 5L, 3L);
        assertThat(views).allMatch(view -> view.shopId() == 1L);
        assertThat(reservationQueryRepository.count(search)).isEqualTo(3);
    }

    @Test
    @DisplayName("고객 범위로 조회하면 여러 매장에 걸친 본인 예약만 나온다")
    void search_userScope_returnsUserReservations() {
        ReservationSearch search = new ReservationSearch(null, 1L, NOT_EXPIRED, null, null);

        List<ReservationView> views = reservationQueryRepository.search(search, 0, 20);

        assertThat(views).extracting(ReservationView::reservationId).containsExactly(2L, 1L, 3L, 4L);
    }

    @Test
    @DisplayName("상태·시작 날짜 조건과 페이징이 적용된다")
    void search_statusDateAndPaging_applied() {
        ReservationSearch confirmed = new ReservationSearch(1L, null, EnumSet.of(ReservationStatus.CONFIRMED),
                LocalDateTime.of(2026, 11, 3, 0, 0), LocalDateTime.of(2026, 11, 4, 0, 0));
        ReservationSearch all = new ReservationSearch(1L, null, NOT_EXPIRED, null, null);

        assertThat(reservationQueryRepository.search(confirmed, 0, 20))
                .extracting(ReservationView::reservationId).containsExactly(5L);
        assertThat(reservationQueryRepository.search(all, 1, 1))
                .extracting(ReservationView::reservationId).containsExactly(5L);
    }

    @Test
    @DisplayName("종료 시각 기준으로 나누면 한 예약이 양쪽에 겹치지 않고 전체를 나눈다")
    void search_endTime_splitsWithoutOverlap() {
        LocalDateTime pivot = LocalDateTime.of(2026, 11, 3, 0, 0);
        ReservationSearch upcoming = new ReservationSearch(1L, null, NOT_EXPIRED, null, null, pivot, null);
        ReservationSearch past = new ReservationSearch(1L, null, NOT_EXPIRED, null, null, null, pivot);
        ReservationSearch all = new ReservationSearch(1L, null, NOT_EXPIRED, null, null);

        assertThat(reservationQueryRepository.search(upcoming, 0, 20)).allMatch(view -> view.endsAt().isAfter(pivot));
        assertThat(reservationQueryRepository.search(past, 0, 20)).noneMatch(view -> view.endsAt().isAfter(pivot));
        assertThat(reservationQueryRepository.count(upcoming) + reservationQueryRepository.count(past))
                .isEqualTo(reservationQueryRepository.count(all));
    }

    @Test
    @DisplayName("상세에는 예약자·결제·취소가 함께 담긴다")
    void findOne_cancelledReservation_includesCustomerPaymentAndCancel() {
        ReservationView view = reservationQueryRepository.findOne(3L, 1L, null).orElseThrow();

        assertThat(view.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(view.customerName()).isEqualTo("고객일");
        assertThat(view.treatmentName()).isEqualTo("손 케어");
        assertThat(view.paymentStatus()).isEqualTo("REFUNDED");
        assertThat(view.cancelStatus()).isEqualTo("COMPLETED");
        assertThat(view.refundAmount()).isEqualTo(18000);
    }

    @Test
    @DisplayName("결제·취소가 없으면 해당 값은 null 이다")
    void findOne_expiredReservation_hasNoPaymentOrCancel() {
        ReservationView view = reservationQueryRepository.findOne(6L, 1L, null).orElseThrow();

        assertThat(view.paymentStatus()).isNull();
        assertThat(view.paymentAmount()).isNull();
        assertThat(view.cancelStatus()).isNull();
    }

    @Test
    @DisplayName("다른 매장·다른 고객의 예약은 찾지 못한다")
    void findOne_otherScope_returnsEmpty() {
        assertThat(reservationQueryRepository.findOne(4L, 1L, null)).isEmpty();
        assertThat(reservationQueryRepository.findOne(5L, null, 1L)).isEmpty();
    }
}
