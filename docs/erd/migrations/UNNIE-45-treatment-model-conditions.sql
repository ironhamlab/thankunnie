-- 기존 Oracle DB에 1회 적용 후 애플리케이션을 기동한다.
-- 기존 모델의 참가비는 확인되지 않았으므로 임의로 채우지 않는다.
ALTER TABLE TREATMENT ADD (PARTICIPATION_FEE NUMBER(10));
