-- Oracle 21c XE용 기존 DB 변경. 애플리케이션을 중지하고 새 버전 배포 전에 1회 적용한다.
-- TREATMENT.CATEGORY_ID가 존재하는 모든 Oracle DB가 대상이다.
-- UNNIE-32-treatment-category.sql을 적용한 DB와 해당 컬럼이 포함된 변경 전 전체 DDL로 만든 DB를 모두 포함한다.
-- 해당 컬럼이 없는 기존 DB와 최신 01_schema_ddl.sql로 생성한 새 DB에는 실행하지 않는다.
-- 실행 전 대상 스키마와 백업을 확인한다. Oracle DDL은 자동 커밋된다.
-- 시술별 카테고리 값은 삭제되고, 이후 모든 시술은 SHOP.CATEGORY_ID를 따른다.
-- 시술·매장·예약 행 및 SHOP.CATEGORY_ID와 CATEGORY 테이블은 유지한다.
ALTER TABLE TREATMENT DROP CONSTRAINT FK_TREATMENT_CATEGORY;
ALTER TABLE TREATMENT DROP COLUMN CATEGORY_ID;
