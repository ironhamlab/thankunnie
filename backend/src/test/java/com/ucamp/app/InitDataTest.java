package com.ucamp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ucamp.app.domain.reservation.entity.Reservation;
import com.ucamp.app.domain.reservation.repository.ReservationRepository;
import com.ucamp.app.domain.schedule.entity.BusinessDay;
import com.ucamp.app.domain.schedule.entity.ShopBusinessHour;
import com.ucamp.app.domain.schedule.repository.ShopBusinessHourRepository;

// local 프로필에서 자동으로 넣는 초기 데이터를 test 프로필 H2 에 넣어 스키마·엔티티와 맞는지 확인한다
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.data-locations=classpath:init-data.sql")
class InitDataTest {

    private static final long SHOP_1 = 1L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShopBusinessHourRepository shopBusinessHourRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    @DisplayName("테스트 사용자에 맞는 회원·매장이 들어간다")
    void load_localInitData_insertsTestUsersAndShops() {
        assertThat(jdbcTemplate.queryForList("SELECT USER_ID FROM APP_USER ORDER BY USER_ID", Long.class))
                .containsExactly(1L, 2L, 21L);
        assertThat(jdbcTemplate.queryForObject("SELECT ROLE FROM APP_USER WHERE USER_ID = 21", String.class))
                .isEqualTo("ADMIN");
        assertThat(jdbcTemplate.queryForList("SELECT SHOP_ID FROM SHOP ORDER BY SHOP_ID", Long.class))
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("영업시간은 엔티티로 읽히고 매장 1 은 일요일이 휴무다")
    void findByShopId_initDataShop1_readsSevenDaysWithSundayClosed() {
        List<ShopBusinessHour> hours = shopBusinessHourRepository.findByShopId(SHOP_1);

        assertThat(hours).hasSize(7);
        assertThat(hours).filteredOn(hour -> hour.getDayOfWeek() == BusinessDay.SUN)
                .singleElement()
                .satisfies(hour -> assertThat(hour.isClosed()).isTrue());
    }

    @Test
    @DisplayName("매장 1 의 11/2~11/4 활성 예약은 확정 예약만 조회되고 취소·만료는 빠진다")
    void findActiveOverlapping_initDataShop1_excludesCancelledAndExpired() {
        List<Reservation> active = reservationRepository.findActiveOverlapping(SHOP_1,
                LocalDateTime.of(2026, 11, 2, 0, 0), LocalDateTime.of(2026, 11, 6, 0, 0));

        assertThat(active).extracting(Reservation::getId).containsExactly(1L, 5L);
    }

    @Test
    @DisplayName("시퀀스는 초기 데이터 ID 와 겹치지 않도록 1000 부터 발급한다")
    void nextval_afterInitData_startsFrom1000() {
        Long nextReservationId = jdbcTemplate.queryForObject("SELECT SEQ_RESERVATION.NEXTVAL FROM DUAL", Long.class);

        assertThat(nextReservationId).isGreaterThanOrEqualTo(1000L);
    }
}
