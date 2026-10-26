package com.ucamp.app.common.lock;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/**
 * 회원 단위 비관적 잠금. 같은 고객이 서로 다른 매장에 동시에 예약하면 매장 잠금이 달라 함께 통과하므로,
 * 본인 예약 겹침을 검사하는 작업(고객 예약 점유)을 이 잠금으로 줄 세운다.
 * 호출한 트랜잭션이 끝날 때 잠금이 풀린다. ShopLock 과 함께 쓸 때는 UserLock 을 먼저 잡는다.
 * 회원 엔티티에 의존하지 않도록 ShopLock 과 같이 APP_USER 테이블을 직접 잠근다.
 */
@Component
public class UserLock {

    private static final String LOCK_SQL = "SELECT USER_ID FROM APP_USER WHERE USER_ID = ? FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;

    public UserLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long userId) {
        List<Long> locked = jdbcTemplate.queryForList(LOCK_SQL, Long.class, userId);
        if (locked.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
