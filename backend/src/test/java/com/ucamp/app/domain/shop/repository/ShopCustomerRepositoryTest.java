package com.ucamp.app.domain.shop.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.config.TimeConfig;
import com.ucamp.app.domain.shop.entity.ShopCustomerMemo;
import com.ucamp.app.domain.shop.policy.CustomerSegment;

// 초기 데이터 매장 1: 고객 1(고객일) 확정 1(11/2 14:00~15:00, 30,000)·취소 완료 3(11/5),
// 고객 2(고객이) 확정 5(11/3 18:00~19:00, 30,000)·만료 6. 매장 2: 고객 1 확정 2(10/10)·취소 처리 중 4
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TimeConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class ShopCustomerRepositoryTest {

    // 고객 1 의 11/2 예약은 끝났고, 고객 2 의 11/3 예약은 아직 남았다
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 16, 0);
    private static final int MIN = CustomerSegment.RETURNING_MIN_VISITS;

    @Autowired
    private ShopCustomerRepository shopCustomerRepository;

    @Autowired
    private ShopCustomerMemoRepository shopCustomerMemoRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("고객별로 끝난 확정 예약만 이용으로 세고, 최근 이용 순(이용 없는 고객은 뒤)으로 담는다")
    void findCustomers_shop1_aggregatesVisitsInRecentOrder() {
        List<ShopCustomerRepository.CustomerRow> rows = find("%", "ALL", "RECENT");

        assertThat(rows).extracting(ShopCustomerRepository.CustomerRow::getUserId).containsExactly(1L, 2L);
        ShopCustomerRepository.CustomerRow first = rows.get(0);
        assertThat(first.getName()).isEqualTo("고객일");
        assertThat(first.getVisitCount()).isEqualTo(1L);
        assertThat(first.getTotalAmount()).isEqualTo(30000L);
        assertThat(first.getFirstVisitAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 14, 0));
        assertThat(first.getLastVisitAt()).isEqualTo(LocalDateTime.of(2026, 11, 2, 14, 0));
        assertThat(first.getNextReservationAt()).isNull();
        assertThat(first.getHasMemo()).isZero();
        ShopCustomerRepository.CustomerRow second = rows.get(1);
        assertThat(second.getVisitCount()).isZero();
        assertThat(second.getLastVisitAt()).isNull();
        assertThat(second.getNextReservationAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 18, 0));
        assertThat(shopCustomerRepository.countCustomers(1L, NOW, "%", "ALL", MIN)).isEqualTo(2);
    }

    @Test
    @DisplayName("이용 2회 이상은 재방문, 나머지는 신규로 거르고, 이용 횟수 순으로 정렬할 수 있다")
    void findCustomers_segmentAndVisitsSort() {
        insertConfirmed(8, 2, LocalDateTime.of(2026, 10, 20, 10, 0));
        insertConfirmed(9, 2, LocalDateTime.of(2026, 10, 25, 10, 0));

        assertThat(find("%", "RETURNING", "RECENT"))
                .extracting(ShopCustomerRepository.CustomerRow::getUserId).containsExactly(2L);
        assertThat(find("%", "NEW", "RECENT"))
                .extracting(ShopCustomerRepository.CustomerRow::getUserId).containsExactly(1L);
        assertThat(find("%", "ALL", "VISITS"))
                .extracting(ShopCustomerRepository.CustomerRow::getUserId).containsExactly(2L, 1L);
        assertThat(shopCustomerRepository.countCustomers(1L, NOW, "%", "RETURNING", MIN)).isEqualTo(1);
    }

    @Test
    @DisplayName("이름·연락처 일부로 찾고, 메모가 있으면 표시하며, 페이지를 나눈다")
    void findCustomers_keywordMemoAndPaging() {
        shopCustomerMemoRepository.saveAndFlush(ShopCustomerMemo.create(1L, 1L, "누드톤 선호"));

        assertThat(find("%고객이%", "ALL", "RECENT"))
                .extracting(ShopCustomerRepository.CustomerRow::getUserId).containsExactly(2L);
        assertThat(find("%1111%", "ALL", "RECENT"))
                .extracting(ShopCustomerRepository.CustomerRow::getUserId).containsExactly(1L);
        assertThat(find("%", "ALL", "RECENT").get(0).getHasMemo()).isEqualTo(1L);
        assertThat(shopCustomerRepository.findCustomers(1L, NOW, "%", "ALL", MIN, "RECENT", 1, 1))
                .extracting(ShopCustomerRepository.CustomerRow::getUserId).containsExactly(2L);
    }

    @Test
    @DisplayName("고객 한 명 집계는 이 매장 확정·취소 완료 예약이 있을 때만 나온다")
    void findCustomer_scope() {
        assertThat(shopCustomerRepository.findCustomer(1L, 1L, NOW)).get()
                .extracting(ShopCustomerRepository.CustomerRow::getVisitCount).isEqualTo(1L);
        // 관리자 계정은 매장 1 예약이 없고, 매장 2 에서 고객 2 는 예약이 없다
        assertThat(shopCustomerRepository.findCustomer(1L, 21L, NOW)).isEmpty();
        assertThat(shopCustomerRepository.findCustomer(2L, 2L, NOW)).isEmpty();
        assertThat(shopCustomerRepository.isCustomer(1L, 1L)).isTrue();
        assertThat(shopCustomerRepository.isCustomer(2L, 2L)).isFalse();
    }

    @Test
    @DisplayName("예약 이력은 이 매장 확정·취소 완료 예약을 최근 시작 순으로 담고 취소 유형을 붙인다")
    void findHistory_shop1Customer1() {
        List<ShopCustomerRepository.HistoryRow> history = shopCustomerRepository.findHistory(1L, 1L);

        assertThat(history).extracting(ShopCustomerRepository.HistoryRow::getReservationId).containsExactly(3L, 1L);
        assertThat(history.get(0).getStatus()).isEqualTo("CANCELLED");
        assertThat(history.get(0).getCancelType()).isEqualTo("CUSTOMER");
        assertThat(history.get(1).getTreatmentName()).isEqualTo("젤네일 원컬러");
        assertThat(history.get(1).getAppliedPrice()).isEqualTo(30000L);
        assertThat(history.get(1).getCancelType()).isNull();
    }

    private List<ShopCustomerRepository.CustomerRow> find(String pattern, String segment, String sort) {
        return shopCustomerRepository.findCustomers(1L, NOW, pattern, segment, MIN, sort, 0, 20);
    }

    private void insertConfirmed(long id, long userId, LocalDateTime startsAt) {
        LocalDateTime createdAt = startsAt.minusDays(5);
        entityManager.createNativeQuery("""
                INSERT INTO RESERVATION (RESERVATION_ID, USER_ID, TREATMENT_ID, STATUS, STARTS_AT, ENDS_AT,
                                         APPLIED_PRICE, DEPOSIT, HOLD_EXPIRES_AT, CONFIRMED_AT, CREATED_AT, UPDATED_AT)
                VALUES (:id, :userId, 1, 'CONFIRMED', :startsAt, :endsAt, 30000, 20000, :holdExpiresAt, :createdAt,
                        :createdAt, :createdAt)
                """)
                .setParameter("id", id)
                .setParameter("userId", userId)
                .setParameter("startsAt", startsAt)
                .setParameter("endsAt", startsAt.plusHours(1))
                .setParameter("holdExpiresAt", createdAt.plusMinutes(5))
                .setParameter("createdAt", createdAt)
                .executeUpdate();
    }
}
