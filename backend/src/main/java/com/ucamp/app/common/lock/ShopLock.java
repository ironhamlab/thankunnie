package com.ucamp.app.common.lock;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ucamp.app.common.exception.BusinessException;
import com.ucamp.app.common.exception.ErrorCode;

/**
 * 매장 단위 비관적 잠금. 매장당 동시 시술 1건이므로 같은 매장의 시간을 바꾸는 작업
 * (고객 예약 점유, 사장님 시간대 닫기 등)을 이 잠금으로 줄 세운다.
 * 호출한 트랜잭션이 끝날 때 잠금이 풀린다. 잠근 뒤 겹침을 검사하고 저장한다.
 */
@Component
public class ShopLock {

    private static final String LOCK_SQL = "SELECT SHOP_ID FROM SHOP WHERE SHOP_ID = ? FOR UPDATE";

    private final JdbcTemplate jdbcTemplate;

    public ShopLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long shopId) {
        List<Long> locked = jdbcTemplate.queryForList(LOCK_SQL, Long.class, shopId);
        if (locked.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
