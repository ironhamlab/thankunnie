-- =====================================================================
-- 땡잡은 언니 스키마 (Oracle 21c XE 기준, 로컬·테스트 H2(MODE=Oracle) 공용)
-- 기준: ERDCloud "땡잡은언니" 2026-10-23
--
-- - 이 파일이 스키마의 기준이다. 테이블·컬럼을 바꾸면 같은 PR 에서 이 파일을 함께 수정하고,
--   기존 DB 에 적용할 ALTER SQL 을 PR 에 적는다.
-- - 로컬·테스트 H2 는 앱 시작 시 이 파일로 테이블을 만든다 (build.gradle 에서 classpath:schema.sql 로 복사).
--   Oracle·H2 양쪽에서 동작하는 문법만 사용한다.
-- - PK 는 시퀀스(SEQ_{테이블})로 발급한다. 엔티티는 allocationSize = 1 로 맞춘다.
--   시퀀스는 1000 부터 시작한다. 1~999 는 02_init_data_dml.sql 초기 데이터가 고정 ID 로 쓴다.
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
CREATE SEQUENCE SEQ_CATEGORY START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_APP_USER START WITH 1000 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 약관 (코드·버전 단위 마스터. 본문은 프론트에서 관리)
-- ---------------------------------------------------------------------
CREATE TABLE TERMS (
    TERMS_ID        NUMBER(19)          NOT NULL,
    CODE            VARCHAR2(30 CHAR)   NOT NULL,   -- AGE_14, SERVICE, PRIVACY, THIRD_PARTY, SMS_NOTIFICATION, MODEL_PHOTO
    VERSION         NUMBER(10)          NOT NULL,
    TITLE           VARCHAR2(100 CHAR)  NOT NULL,
    IS_REQUIRED     CHAR(1)             NOT NULL,   -- Y, N (Y: 회원가입 필수 동의)
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_TERMS PRIMARY KEY (TERMS_ID),
    CONSTRAINT UK_TERMS_CODE_VERSION UNIQUE (CODE, VERSION)
);
CREATE SEQUENCE SEQ_TERMS START WITH 1000 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 회원 약관 동의 (회원·약관 버전당 1행. WITHDRAWN_AT: 선택 약관 철회 시각, 다시 동의하면 NULL)
-- ---------------------------------------------------------------------
CREATE TABLE USER_TERMS_AGREEMENT (
    AGREEMENT_ID    NUMBER(19)          NOT NULL,
    USER_ID         NUMBER(19)          NOT NULL,
    TERMS_ID        NUMBER(19)          NOT NULL,
    AGREED_AT       TIMESTAMP(3)        NOT NULL,
    WITHDRAWN_AT    TIMESTAMP(3),
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_USER_TERMS_AGREEMENT PRIMARY KEY (AGREEMENT_ID),
    CONSTRAINT UK_USER_TERMS_AGREEMENT UNIQUE (USER_ID, TERMS_ID),
    CONSTRAINT FK_USER_TERMS_AGREEMENT_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID),
    CONSTRAINT FK_USER_TERMS_AGREEMENT_TERMS FOREIGN KEY (TERMS_ID) REFERENCES TERMS (TERMS_ID)
);
CREATE SEQUENCE SEQ_USER_TERMS_AGREEMENT START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_SHOP START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_SHOP_BUSINESS_HOUR START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_STAFF START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_TREATMENT START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_RESERVATION START WITH 1000 INCREMENT BY 1;
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
CREATE SEQUENCE SEQ_DEPOSIT_PAYMENT START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_RESERVATION_CANCEL START WITH 1000 INCREMENT BY 1;

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
CREATE SEQUENCE SEQ_SHOP_SCHEDULE_EXCEPTION START WITH 1000 INCREMENT BY 1;
CREATE INDEX IDX_SHOP_SCHEDULE_EXCEPTION_SHOP_ID ON SHOP_SCHEDULE_EXCEPTION (SHOP_ID, STARTS_AT);

-- ---------------------------------------------------------------------
-- 구독(찜) — 선택 기능
-- ---------------------------------------------------------------------
CREATE TABLE NOTIFICATION_SUBSCRIPTION (
    SUBSCRIPTION_ID     NUMBER(19)          NOT NULL,
    USER_ID             NUMBER(19)          NOT NULL,
    SHOP_ID             NUMBER(19),
    TREATMENT_ID        NUMBER(19),                     -- STORE 알림만. 고객이 고른 시술. 시간 없는 찜과 카테고리 구독은 비운다
    SUBSCRIPTION_TYPE   VARCHAR2(20 CHAR)   NOT NULL,   -- STORE(매장), CATEGORY_REGION(카테고리+지역)
    CATEGORY_ID         NUMBER(19),                     -- CATEGORY_REGION 구독만
    REGION_CODE         VARCHAR2(20 CHAR),              -- CATEGORY_REGION 구독만
    START_TIME          TIMESTAMP(0),                   -- 알림 희망 시작. 업장 찜만 비울 수 있고, 비우면 알림을 보내지 않는다
    END_TIME            TIMESTAMP(0),                   -- 알림 희망 종료. 시작과 함께 비우거나 함께 넣는다
    STATUS              VARCHAR2(10 CHAR)   DEFAULT 'ACTIVE' NOT NULL,  -- ACTIVE, INACTIVE
    CREATED_AT          TIMESTAMP(3)        NOT NULL,
    UPDATED_AT          TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_NOTIFICATION_SUBSCRIPTION PRIMARY KEY (SUBSCRIPTION_ID),
    CONSTRAINT FK_NOTIFICATION_SUBSCRIPTION_APP_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID),
    CONSTRAINT FK_NOTIFICATION_SUBSCRIPTION_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID),
    CONSTRAINT FK_NOTIFICATION_SUBSCRIPTION_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES CATEGORY (CATEGORY_ID)
);
CREATE SEQUENCE SEQ_NOTIFICATION_SUBSCRIPTION START WITH 1000 INCREMENT BY 1;

-- ---------------------------------------------------------------------
-- 알림 이벤트 (임박·초임박·취소 재공개 시 1건씩 쌓고 이후 매칭·발송한다)
-- 임박·초임박은 OPEN 행이 있을 수 있고, 재공개는 영업시간만으로도 생길 수 있어 EXCEPTION_ID 는 비울 수 있다.
-- ---------------------------------------------------------------------
CREATE TABLE NOTIFICATION_EVENT (
    EVENT_ID        NUMBER(19)          NOT NULL,
    EVENT_TYPE      VARCHAR2(20 CHAR)   NOT NULL,   -- IMMINENT, LAST_MINUTE, REOPENED
    EXCEPTION_ID    NUMBER(19),                     -- OPEN 행 ID. 없으면 NULL. FK 없음
    SHOP_ID         NUMBER(19)          NOT NULL,
    RESERVATION_ID  NUMBER(19),                     -- 취소 재공개만, 취소된 예약
    SLOT_STARTS_AT  TIMESTAMP(0)        NOT NULL,   -- 임박: OPEN 시작, 재공개: 취소된 예약 시작
    SLOT_ENDS_AT    TIMESTAMP(0)        NOT NULL,   -- 종료 미포함
    STATUS          VARCHAR2(10 CHAR)   DEFAULT 'PENDING' NOT NULL,  -- PENDING, PROCESSED
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    -- 유니크에 넣는 예약 번호. 재공개는 취소된 예약 번호, 임박·초임박은 예약이 없으므로 0.
    -- 예약 번호 칸을 그대로 넣으면 임박의 빈 값이 서로 다르다고 봐서 같은 임박이 여러 건 생긴다.
    UNIQUE_RESERVATION_ID NUMBER(19) GENERATED ALWAYS AS (
        CASE WHEN EVENT_TYPE = 'REOPENED' THEN RESERVATION_ID ELSE 0 END
    ),
    CONSTRAINT PK_NOTIFICATION_EVENT PRIMARY KEY (EVENT_ID),
    -- 임박·초임박: 매장+종류+시작 시각당 1건(예약 키는 0). 재공개: 예약이 다르면 같은 시각도 각각 1건.
    CONSTRAINT UK_NOTIFICATION_EVENT_SLOT UNIQUE (SHOP_ID, EVENT_TYPE, SLOT_STARTS_AT, UNIQUE_RESERVATION_ID),
    CONSTRAINT FK_NOTIFICATION_EVENT_SHOP FOREIGN KEY (SHOP_ID) REFERENCES SHOP (SHOP_ID),
    CONSTRAINT FK_NOTIFICATION_EVENT_RESERVATION FOREIGN KEY (RESERVATION_ID) REFERENCES RESERVATION (RESERVATION_ID)
);
CREATE SEQUENCE SEQ_NOTIFICATION_EVENT START WITH 1000 INCREMENT BY 1;
CREATE INDEX IDX_NOTIFICATION_EVENT_STATUS ON NOTIFICATION_EVENT (STATUS, EVENT_ID);

-- ---------------------------------------------------------------------
-- 발송 대기 작업 (알림 이벤트 1건을 회원 1명에게 보내기 전의 한 줄)
-- ---------------------------------------------------------------------
CREATE TABLE NOTIFICATION_DISPATCH (
    DISPATCH_ID     NUMBER(19)          NOT NULL,
    EVENT_ID        NUMBER(19)          NOT NULL,
    USER_ID         NUMBER(19)          NOT NULL,   -- 알림을 받을 회원
    -- PENDING(대기), PROCESSING(처리 중), SKIPPED(재검사 탈락), SENT(발송 완료),
    -- FAILED(발송 실패), UNKNOWN(결과 불명)
    STATUS          VARCHAR2(20 CHAR)   DEFAULT 'PENDING' NOT NULL,
    FAIL_REASON     VARCHAR2(500 CHAR),             -- FAILED·UNKNOWN 일 때만 사유. SENT·SKIPPED 는 NULL
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_NOTIFICATION_DISPATCH PRIMARY KEY (DISPATCH_ID),
    -- 서버가 다시 떠도 같은 이벤트·회원 작업은 두 줄이 되지 않는다
    CONSTRAINT UK_NOTIFICATION_DISPATCH_EVENT_USER UNIQUE (EVENT_ID, USER_ID),
    CONSTRAINT FK_NOTIFICATION_DISPATCH_EVENT FOREIGN KEY (EVENT_ID) REFERENCES NOTIFICATION_EVENT (EVENT_ID),
    CONSTRAINT FK_NOTIFICATION_DISPATCH_APP_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID)
);
CREATE SEQUENCE SEQ_NOTIFICATION_DISPATCH START WITH 1000 INCREMENT BY 1;
CREATE INDEX IDX_NOTIFICATION_DISPATCH_STATUS ON NOTIFICATION_DISPATCH (STATUS, DISPATCH_ID);

-- ---------------------------------------------------------------------
-- 받은 알림 (문자 발송에 성공한 뒤 종 목록에 보여줄 한 줄)
-- 발송 큐(NOTIFICATION_DISPATCH)와 따로 둔다. 매장 ID 는 두지 않고, 이동은 LINK_PATH 에 적는다.
-- ---------------------------------------------------------------------
CREATE TABLE NOTIFICATION_INBOX (
    INBOX_ID        NUMBER(19)          NOT NULL,
    USER_ID         NUMBER(19)          NOT NULL,   -- 알림을 받은 회원
    CATEGORY        VARCHAR2(20 CHAR)   NOT NULL,   -- RESERVATION(예약 확정·취소), INTEREST(빈자리·임박·초임박·묶음)
    TITLE           VARCHAR2(200 CHAR)  NOT NULL,   -- 종 목록에 보이는 제목
    BODY            VARCHAR2(500 CHAR)  NOT NULL,   -- 보낸 문자와 같은 본문
    LINK_PATH       VARCHAR2(200 CHAR),             -- 누르면 이동할 화면 경로. 없으면 NULL
    READ_AT         TIMESTAMP(3),                   -- 읽은 시각. NULL 이면 안 읽음
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,   -- 읽음 처리처럼 이 줄이 바뀔 때
    CONSTRAINT PK_NOTIFICATION_INBOX PRIMARY KEY (INBOX_ID),
    CONSTRAINT FK_NOTIFICATION_INBOX_APP_USER FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID)
);
CREATE SEQUENCE SEQ_NOTIFICATION_INBOX START WITH 1000 INCREMENT BY 1;
CREATE INDEX IDX_NOTIFICATION_INBOX_USER ON NOTIFICATION_INBOX (USER_ID, CREATED_AT);

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
CREATE SEQUENCE SEQ_SETTLEMENT START WITH 1000 INCREMENT BY 1;

-- 이미지 파일 삭제 실패 재시도. 매장 삭제 이후에도 정리할 수 있도록 SHOP FK는 두지 않는다.
CREATE TABLE SHOP_IMAGE_CLEANUP (
    CLEANUP_ID      NUMBER(19)          NOT NULL,
    STORAGE_SCOPE   VARCHAR2(512 CHAR)   NOT NULL,
    FILE_NAME       VARCHAR2(40 CHAR)    NOT NULL,
    ATTEMPT_COUNT   NUMBER(10)          DEFAULT 0 NOT NULL,
    NEXT_ATTEMPT_AT TIMESTAMP(3)        NOT NULL,
    CREATED_AT      TIMESTAMP(3)        NOT NULL,
    UPDATED_AT      TIMESTAMP(3)        NOT NULL,
    CONSTRAINT PK_SHOP_IMAGE_CLEANUP PRIMARY KEY (CLEANUP_ID),
    CONSTRAINT UK_SHOP_IMAGE_CLEANUP_OBJECT UNIQUE (STORAGE_SCOPE, FILE_NAME)
);
CREATE SEQUENCE SEQ_SHOP_IMAGE_CLEANUP START WITH 1000 INCREMENT BY 1;
CREATE INDEX IDX_SHOP_IMG_CLEANUP_DUE ON SHOP_IMAGE_CLEANUP (STORAGE_SCOPE, NEXT_ATTEMPT_AT);
