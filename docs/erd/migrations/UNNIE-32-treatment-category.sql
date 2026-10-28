-- 과거 UNNIE-32 배포 이력용. UNNIE-111 이후 신규 적용하지 않는다.
-- 이미 적용한 DB는 UNNIE-111-remove-treatment-category.sql로 시술 카테고리를 제거한다.
-- Oracle 21c XE용 기존 DB 변경. 새 DB는 01_schema_ddl.sql을 사용한다.
-- 기존 시술의 최초 카테고리는 소속 매장 카테고리로 채우고, 이후 시술마다 수정한다.
-- 실행 전 백업과 대상 스키마 확인 필요. Oracle DDL은 자동 커밋된다.
ALTER TABLE TREATMENT ADD (CATEGORY_ID NUMBER(19));

UPDATE TREATMENT t
SET CATEGORY_ID = (SELECT s.CATEGORY_ID FROM SHOP s WHERE s.SHOP_ID = t.SHOP_ID)
WHERE t.CATEGORY_ID IS NULL;

ALTER TABLE TREATMENT MODIFY (CATEGORY_ID NOT NULL);
ALTER TABLE TREATMENT ADD CONSTRAINT FK_TREATMENT_CATEGORY
    FOREIGN KEY (CATEGORY_ID) REFERENCES CATEGORY (CATEGORY_ID);
