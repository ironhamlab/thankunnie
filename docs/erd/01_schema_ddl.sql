-- =====================================================================
-- 땡잡은 언니 스키마 (Oracle 21c XE 기준, 로컬·테스트 H2(MODE=Oracle) 공용)
-- 기준: ERDCloud "땡잡은언니" 2026-10-23
--
-- - 이 파일이 스키마의 기준이다. 테이블·컬럼을 바꾸면 같은 PR 에서 이 파일을 함께 수정하고,
--   기존 DB 에 적용할 ALTER SQL 을 PR 에 적는다.
-- - 로컬·테스트 H2 는 앱 시작 시 이 파일로 테이블을 만든다 (build.gradle 에서 classpath:schema.sql 로 복사).
--   Oracle·H2 양쪽에서 동작하는 문법만 사용한다.
-- - PK 는 시퀀스(SEQ_{테이블})로 발급한다. 엔티티는 allocationSize = 1 로 맞춘다.
-- - 시간 구간(starts_at ~ ends_at)의 종료 시각은 포함하지 않는다.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 매장카테고리
-- ---------------------------------------------------------------------
CREATE TABLE CATEGORY (
    CATEGORY_ID     NUMBER(19)          NOT NULL,
    NAME            VARCHAR2(50 CHAR)   NOT NULL,   -- 카테고리명
    CONSTRAINT PK_CATEGORY PRIMARY KEY (CATEGORY_ID),
    CONSTRAINT UK_CATEGORY_NAME UNIQUE (NAME)
);
CREATE SEQUENCE SEQ_CATEGORY START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 회원 (USER 는 예약어라 APP_USER. PK 컬럼은 USER_ID 유지)
-- ---------------------------------------------------------------------
CREATE TABLE APP_USER (
    USER_ID         NUMBER(19)          NOT NULL,
    LOGIN_ID        VARCHAR2(100 CHAR)  NOT NULL,
    PASSWORD_HASH   VARCHAR2(255 CHAR)  NOT NULL,
    NAME            VARCHAR2(50 CHAR)   NOT NULL,
    PHONE           VARCHAR2(20 CHAR)   NOT NULL,
    STATUS          VARCHAR2(10 CHAR)   DEFAULT 'ACTIVE' NOT NULL,  -- ACTIVE, INACTIVE
    ROLE            VARCHAR2(10 CHAR)   DEFAULT 'USER' NOT NULL,    -- USER, ADMIN
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_APP_USER PRIMARY KEY (USER_ID),
    CONSTRAINT UK_APP_USER_LOGIN_ID UNIQUE (LOGIN_ID)
);
CREATE SEQUENCE SEQ_APP_USER START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 매장 (사장님 로그인 정보 포함, 계정당 매장 1개)
-- ---------------------------------------------------------------------
CREATE TABLE SHOP (
    SHOP_ID         NUMBER(19)          NOT NULL,
    CATEGORY_ID     NUMBER(19)          NOT NULL,
    LOGIN_ID        VARCHAR2(100 CHAR)  NOT NULL,
    PASSWORD_HASH   VARCHAR2(255 CHAR)  NOT NULL,
    NAME            VARCHAR2(100 CHAR)  NOT NULL,   -- 매장명
    REGION_CODE     VARCHAR2(20 CHAR)   NOT NULL,
    ADDRESS         VARCHAR2(300 CHAR)  NOT NULL,
    ADDRESS_DETAIL  VARCHAR2(200 CHAR),
    LATITUDE        NUMBER(10, 7)       NOT NULL,
    LONGITUDE       NUMBER(10, 7)       NOT NULL,
    PHONE           VARCHAR2(20 CHAR)   NOT NULL,
    DESCRIPTION     VARCHAR2(2000 CHAR),
    NOTICE          VARCHAR2(2000 CHAR),
    IMAGE_URL       VARCHAR2(1000 CHAR),
    STATUS          VARCHAR2(10 CHAR)   DEFAULT 'ACTIVE' NOT NULL,  -- ACTIVE, INACTIVE
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_SHOP PRIMARY KEY (SHOP_ID),
    CONSTRAINT UK_SHOP_LOGIN_ID UNIQUE (LOGIN_ID),
    CONSTRAINT FK_SHOP_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES CATEGORY (CATEGORY_ID)
);
CREATE SEQUENCE SEQ_SHOP START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 요일별영업시간 (매주 반복. 일회성 휴무·연장은 SHOP_SCHEDULE_EXCEPTION)
-- ---------------------------------------------------------------------
CREATE TABLE SHOP_BUSINESS_HOUR (
    BUSINESS_HOUR_ID    NUMBER(19)          NOT NULL,
    SHOP_ID             NUMBER(19)          NOT NULL,
    DAY_OF_WEEK         VARCHAR2(3 CHAR)    NOT NULL,   -- MON ~ SUN
    OPEN_TIME           VARCHAR2(5 CHAR),               -- HH:MM, 휴무면 NULL
    CLOSE_TIME          VARCHAR2(5 CHAR),               -- HH:MM, 휴무면 NULL
    IS_CLOSED           CHAR(1)             NOT NULL,   -- Y, N
    CONSTRAINT PK_SHOP_BUSINESS_HOUR PRIMARY KEY (BUSINESS_HOUR_ID),
    -- 매장마다 요일당 1행
    CONSTRAINT UK_SHOP_BUSINESS_HOUR_DAY UNIQUE (SHOP_ID, DAY_OF_WEEK),
    CONSTRAINT FK_SHOP_BUSINESS_HOUR_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID)
);
CREATE SEQUENCE SEQ_SHOP_BUSINESS_HOUR START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 점원
-- ---------------------------------------------------------------------
CREATE TABLE STAFF (
    STAFF_ID        NUMBER(19)          NOT NULL,
    SHOP_ID         NUMBER(19)          NOT NULL,
    NAME            VARCHAR2(50 CHAR)   NOT NULL,
    POSITION        VARCHAR2(10 CHAR)   NOT NULL,   -- 직급
    PHONE           VARCHAR2(20 CHAR)   NOT NULL,
    STATUS          VARCHAR2(10 CHAR)   DEFAULT 'ACTIVE' NOT NULL,  -- ACTIVE, INACTIVE
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_STAFF PRIMARY KEY (STAFF_ID),
    CONSTRAINT FK_STAFF_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID)
);
CREATE SEQUENCE SEQ_STAFF START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 시술 (가격 3종: 초임박 판매가 <= 판매가 <= 정상가격, 100원 단위. 같으면 할인 없음)
-- ---------------------------------------------------------------------
CREATE TABLE TREATMENT (
    TREATMENT_ID        NUMBER(19)          NOT NULL,
    SHOP_ID             NUMBER(19)          NOT NULL,
    STAFF_ID            NUMBER(19),
    NAME                VARCHAR2(100 CHAR)  NOT NULL,   -- 시술명
    DESCRIPTION         VARCHAR2(2000 CHAR),
    REGULAR_PRICE       NUMBER(10)          NOT NULL,   -- 정상가격
    PRICE               NUMBER(10)          NOT NULL,   -- 판매가격 (일반·임박)
    LAST_MINUTE_PRICE   NUMBER(10)          NOT NULL,   -- 초임박 판매가 (시작 3시간 전부터)
    DURATION_MINUTES    NUMBER(4)           NOT NULL,
    TREATMENT_TYPE      VARCHAR2(20 CHAR)   NOT NULL,   -- NORMAL, MODEL
    MODEL_CONDITION     VARCHAR2(2000 CHAR),
    PREPARATION_NOTES   VARCHAR2(1000 CHAR),
    PHOTO_NOTICE        VARCHAR2(1000 CHAR),
    IMAGE_URL           VARCHAR2(1000 CHAR),
    STATUS              VARCHAR2(10 CHAR)   DEFAULT 'ACTIVE' NOT NULL,  -- ACTIVE, INACTIVE
    CREATED_AT          TIMESTAMP(3)        NOT NULL,
    UPDATED_AT          TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_TREATMENT PRIMARY KEY (TREATMENT_ID),
    CONSTRAINT FK_TREATMENT_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID),
    CONSTRAINT FK_TREATMENT_STAFF FOREIGN KEY (STAFF_ID) REFERENCES STAFF (STAFF_ID)
);
CREATE SEQUENCE SEQ_TREATMENT START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 예약 (고객이 예약 클릭할 때 HOLD 로 생성. 가격·예약금은 클릭 시점 값으로 고정)
-- ---------------------------------------------------------------------
CREATE TABLE RESERVATION (
    RESERVATION_ID      NUMBER(19)          NOT NULL,
    USER_ID             NUMBER(19)          NOT NULL,
    TREATMENT_ID        NUMBER(19)          NOT NULL,
    STATUS              VARCHAR2(20 CHAR)   DEFAULT 'HOLD' NOT NULL,  -- HOLD, CONFIRMED, CANCEL_PENDING, CANCELLED, EXPIRED
    STARTS_AT           TIMESTAMP(0)        NOT NULL,
    ENDS_AT             TIMESTAMP(0)        NOT NULL,
    APPLIED_PRICE       NUMBER(10)          NOT NULL,   -- 클릭 시점 적용 판매가
    DEPOSIT             NUMBER(10)          NOT NULL,   -- 예약금 = min(적용 판매가, 20,000)
    HOLD_EXPIRES_AT     TIMESTAMP(3)        NOT NULL,   -- 점유 만료 = 클릭 + 5분
    CONFIRMED_AT        TIMESTAMP(3),
    CUSTOMER_REQUEST    VARCHAR2(1000 CHAR),
    CREATED_AT          TIMESTAMP(3)        NOT NULL,
    UPDATED_AT          TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_RESERVATION PRIMARY KEY (RESERVATION_ID),
    CONSTRAINT FK_RESERVATION_APP_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID),
    CONSTRAINT FK_RESERVATION_TREATMENT FOREIGN KEY (TREATMENT_ID) REFERENCES TREATMENT (TREATMENT_ID)
);
CREATE SEQUENCE SEQ_RESERVATION START WITH 1 INCREMENT BY 1;
-- 고객 내 예약 조회
CREATE INDEX IDX_RESERVATION_USER_ID ON RESERVATION (USER_ID, STARTS_AT);
-- 겹침 검사·빈 시간 계산 (시술 → 매장으로 조인)
CREATE INDEX IDX_RESERVATION_TREATMENT_ID ON RESERVATION (TREATMENT_ID, STARTS_AT);

-- ---------------------------------------------------------------------
-- 예약금결제
-- ---------------------------------------------------------------------
CREATE TABLE DEPOSIT_PAYMENT (
    DEPOSIT_PAYMENT_ID  NUMBER(19)          NOT NULL,
    RESERVATION_ID      NUMBER(19)          NOT NULL,
    PG_PROVIDER         VARCHAR2(20 CHAR)   NOT NULL,
    PG_TRANSACTION_KEY  VARCHAR2(200 CHAR),
    STATUS              VARCHAR2(20 CHAR)   DEFAULT 'READY' NOT NULL,  -- READY, PAID, FAILED, UNKNOWN, REFUND_PENDING, PART_REFUNDED, REFUNDED
    AMOUNT              NUMBER(10)          NOT NULL,
    REFUNDED_AMOUNT     NUMBER(10)          DEFAULT 0 NOT NULL,
    APPROVED_AT         TIMESTAMP(3),
    REFUNDED_AT         TIMESTAMP(3),
    CREATED_AT          TIMESTAMP(3)        NOT NULL,
    UPDATED_AT          TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_DEPOSIT_PAYMENT PRIMARY KEY (DEPOSIT_PAYMENT_ID),
    CONSTRAINT FK_DEPOSIT_PAYMENT_RESERVATION FOREIGN KEY (RESERVATION_ID) REFERENCES RESERVATION (RESERVATION_ID)
);
CREATE SEQUENCE SEQ_DEPOSIT_PAYMENT START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 예약취소
-- ---------------------------------------------------------------------
CREATE TABLE RESERVATION_CANCEL (
    CANCEL_ID               NUMBER(19)          NOT NULL,
    RESERVATION_ID          NUMBER(19)          NOT NULL,
    USER_ID                 NUMBER(19)          NOT NULL,   -- 취소 요청 회원
    CANCEL_TYPE             VARCHAR2(20 CHAR)   NOT NULL,   -- CUSTOMER, SHOP_FAULT, CUSTOMER_REQUEST
    POLICY_REFUND_AMOUNT    NUMBER(10)          NOT NULL,   -- 정책상 환불액
    REFUND_AMOUNT           NUMBER(10)          NOT NULL,   -- 환불 예정 금액
    PG_CANCEL_KEY           VARCHAR2(200 CHAR),
    STATUS                  VARCHAR2(20 CHAR)   NOT NULL,   -- REQUESTED, COMPLETED, FAILED, UNKNOWN
    REQUESTED_AT            TIMESTAMP(3)        NOT NULL,
    COMPLETED_AT            TIMESTAMP(3),
    CREATED_AT              TIMESTAMP(3)        NOT NULL,
    UPDATED_AT              TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_RESERVATION_CANCEL PRIMARY KEY (CANCEL_ID),
    CONSTRAINT FK_RESERVATION_CANCEL_RESERVATION FOREIGN KEY (RESERVATION_ID) REFERENCES RESERVATION (RESERVATION_ID),
    CONSTRAINT FK_RESERVATION_CANCEL_APP_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID),
    CONSTRAINT UK_RESERVATION_CANCEL_RESERVATION_ID UNIQUE (RESERVATION_ID)
);
CREATE SEQUENCE SEQ_RESERVATION_CANCEL START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 무료 취소 사용 기록 (회원당 하루 1번, 한국 날짜 기준)
-- ---------------------------------------------------------------------
CREATE TABLE GRACE_CANCEL_USAGE (
    USER_ID         NUMBER(19)      NOT NULL,
    USED_DATE       DATE            NOT NULL,   -- 한국 날짜 (시각 00:00:00)
    CANCEL_ID       NUMBER(19)      NOT NULL,
    CREATED_AT      TIMESTAMP(3)    NOT NULL,
    UPDATED_AT      TIMESTAMP(3)    NOT NULL,
    -- 같은 날 두 번째 무료 취소는 PK 중복으로 막힌다
    CONSTRAINT PK_GRACE_CANCEL_USAGE PRIMARY KEY (USER_ID, USED_DATE),
    CONSTRAINT FK_GRACE_CANCEL_USAGE_APP_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID),
    CONSTRAINT FK_GRACE_CANCEL_USAGE_RESERVATION_CANCEL FOREIGN KEY (CANCEL_ID) REFERENCES RESERVATION_CANCEL (CANCEL_ID)
);

-- ---------------------------------------------------------------------
-- 매장 예외 일정 (일회성 추가 열기·닫기·임시 휴무)
-- ---------------------------------------------------------------------
CREATE TABLE SHOP_SCHEDULE_EXCEPTION (
    EXCEPTION_ID    NUMBER(19)          NOT NULL,
    SHOP_ID         NUMBER(19)          NOT NULL,
    RESERVATION_ID  NUMBER(19),                     -- 사장님이 예약을 취소하며 닫은 경우만
    EXCEPTION_TYPE  VARCHAR2(10 CHAR)   NOT NULL,   -- OPEN, CLOSE (겹치면 CLOSE 우선)
    STARTS_AT       TIMESTAMP(0)        NOT NULL,   -- 30분 단위
    ENDS_AT         TIMESTAMP(0)        NOT NULL,   -- 30분 단위, 종료 미포함
    REASON          VARCHAR2(500 CHAR),
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_SHOP_SCHEDULE_EXCEPTION PRIMARY KEY (EXCEPTION_ID),
    CONSTRAINT FK_SHOP_SCHEDULE_EXCEPTION_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID),
    CONSTRAINT FK_SHOP_SCHEDULE_EXCEPTION_RESERVATION FOREIGN KEY (RESERVATION_ID) REFERENCES RESERVATION (RESERVATION_ID)
);
CREATE SEQUENCE SEQ_SHOP_SCHEDULE_EXCEPTION START WITH 1 INCREMENT BY 1;
CREATE INDEX IDX_SHOP_SCHEDULE_EXCEPTION_SHOP_ID ON SHOP_SCHEDULE_EXCEPTION (SHOP_ID, STARTS_AT);

-- ---------------------------------------------------------------------
-- 구독(찜) — 선택 기능
-- ---------------------------------------------------------------------
CREATE TABLE NOTIFICATION_SUBSCRIPTION (
    SUBSCRIPTION_ID     NUMBER(19)          NOT NULL,
    USER_ID             NUMBER(19)          NOT NULL,
    SHOP_ID             NUMBER(19),
    SUBSCRIPTION_TYPE   VARCHAR2(20 CHAR)   NOT NULL,   -- STORE, CATEGORY_REGION
    CATEGORY_ID         NUMBER(19),                     -- 카테고리 구독일 때만
    REGION_CODE         VARCHAR2(20 CHAR),
    START_TIME          TIMESTAMP(0)        NOT NULL,   -- 알림 희망 시작
    END_TIME            TIMESTAMP(0)        NOT NULL,   -- 알림 희망 종료
    STATUS              VARCHAR2(10 CHAR)   DEFAULT 'ACTIVE' NOT NULL,  -- ACTIVE, INACTIVE
    CREATED_AT          TIMESTAMP(3)        NOT NULL,
    UPDATED_AT          TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_NOTIFICATION_SUBSCRIPTION PRIMARY KEY (SUBSCRIPTION_ID),
    CONSTRAINT FK_NOTIFICATION_SUBSCRIPTION_APP_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID),
    CONSTRAINT FK_NOTIFICATION_SUBSCRIPTION_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID),
    CONSTRAINT FK_NOTIFICATION_SUBSCRIPTION_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES CATEGORY (CATEGORY_ID)
);
CREATE SEQUENCE SEQ_NOTIFICATION_SUBSCRIPTION START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 알림 이벤트 (빈자리 = 예외 일정 OPEN 행. 임박 진입·취소 재공개 시 1건씩 쌓고 이후 매칭·발송한다)
-- ---------------------------------------------------------------------
CREATE TABLE NOTIFICATION_EVENT (
    EVENT_ID        NUMBER(19)          NOT NULL,
    EVENT_TYPE      VARCHAR2(20 CHAR)   NOT NULL,   -- IMMINENT, REOPENED
    EXCEPTION_ID    NUMBER(19)          NOT NULL,   -- OPEN 행 ID. 담당 도메인이 지울 수 있어 FK 없음
    SHOP_ID         NUMBER(19)          NOT NULL,
    RESERVATION_ID  NUMBER(19),                     -- 취소 재공개만, 취소된 예약
    SLOT_STARTS_AT  TIMESTAMP(0)        NOT NULL,   -- 임박: OPEN 시작, 재공개: 취소된 예약 시작
    SLOT_ENDS_AT    TIMESTAMP(0)        NOT NULL,   -- 종료 미포함
    STATUS          VARCHAR2(10 CHAR)   DEFAULT 'PENDING' NOT NULL,  -- PENDING, PROCESSED
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_NOTIFICATION_EVENT PRIMARY KEY (EVENT_ID),
    -- 같은 빈자리·같은 시각의 같은 이벤트는 한 번만 만든다
    CONSTRAINT UK_NOTIFICATION_EVENT_SLOT UNIQUE (EXCEPTION_ID, EVENT_TYPE, SLOT_STARTS_AT),
    CONSTRAINT FK_NOTIFICATION_EVENT_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID),
    CONSTRAINT FK_NOTIFICATION_EVENT_RESERVATION FOREIGN KEY (RESERVATION_ID) REFERENCES RESERVATION (RESERVATION_ID)
);
CREATE SEQUENCE SEQ_NOTIFICATION_EVENT START WITH 1 INCREMENT BY 1;
CREATE INDEX IDX_NOTIFICATION_EVENT_STATUS ON NOTIFICATION_EVENT (STATUS, EVENT_ID);

-- ---------------------------------------------------------------------
-- 정산 — MVP 범위 밖
-- ---------------------------------------------------------------------
CREATE TABLE SETTLEMENT (
    SETTLEMENT_ID       NUMBER(19)      NOT NULL,
    DEPOSIT_PAYMENT_ID  NUMBER(19)      NOT NULL,
    SHOP_ID             NUMBER(19)      NOT NULL,
    AMOUNT              NUMBER(10)      NOT NULL,   -- 최종금액
    CREATED_AT          TIMESTAMP(3)    NOT NULL,
    CONSTRAINT PK_SETTLEMENT PRIMARY KEY (SETTLEMENT_ID),
    CONSTRAINT FK_SETTLEMENT_DEPOSIT_PAYMENT FOREIGN KEY (DEPOSIT_PAYMENT_ID) REFERENCES DEPOSIT_PAYMENT (DEPOSIT_PAYMENT_ID),
    CONSTRAINT FK_SETTLEMENT_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID)
);
CREATE SEQUENCE SEQ_SETTLEMENT START WITH 1 INCREMENT BY 1;