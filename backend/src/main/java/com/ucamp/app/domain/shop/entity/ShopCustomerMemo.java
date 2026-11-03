package com.ucamp.app.domain.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.ucamp.app.common.entity.BaseTimeEntity;

/** 매장 계정만 보는 고객 메모. 매장·고객당 1행이다. */
@Entity
@Table(name = "SHOP_CUSTOMER_MEMO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopCustomerMemo extends BaseTimeEntity {

    public static final int MEMO_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_shop_customer_memo")
    @SequenceGenerator(name = "seq_shop_customer_memo", sequenceName = "SEQ_SHOP_CUSTOMER_MEMO", allocationSize = 1)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "MEMO_ID", nullable = false)
    private Long memoId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "SHOP_ID", nullable = false)
    private Long shopId;

    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "MEMO", nullable = false, length = MEMO_MAX_LENGTH)
    private String memo;

    private ShopCustomerMemo(Long shopId, Long userId, String memo) {
        this.shopId = shopId;
        this.userId = userId;
        this.memo = memo;
    }

    public static ShopCustomerMemo create(Long shopId, Long userId, String memo) {
        return new ShopCustomerMemo(shopId, userId, memo);
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }
}
