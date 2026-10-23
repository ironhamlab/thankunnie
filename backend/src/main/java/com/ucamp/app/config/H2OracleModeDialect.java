package com.ucamp.app.config;

import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.type.SqlTypes;

/**
 * 로컬·테스트 H2(MODE=Oracle)용 방언.
 * H2 는 Oracle DDL 의 NUMBER(19)·NUMBER(10) 컬럼을 NUMERIC 으로 보고하므로, ddl-auto=validate 에서
 * Long·Integer 필드(BIGINT·INTEGER 기대)가 실패하지 않도록 정수 타입과 NUMERIC 을 같은 타입으로 본다.
 * 실제 Oracle 은 타입 이름(NUMBER)으로 검증되므로 이 방언을 쓰지 않는다.
 */
public class H2OracleModeDialect extends H2Dialect {

    public H2OracleModeDialect(DialectResolutionInfo info) {
        super(info);
    }

    @Override
    public boolean equivalentTypes(int typeCode1, int typeCode2) {
        return super.equivalentTypes(typeCode1, typeCode2)
                || isIntegralAndNumeric(typeCode1, typeCode2)
                || isIntegralAndNumeric(typeCode2, typeCode1);
    }

    private static boolean isIntegralAndNumeric(int integralCode, int numericCode) {
        return SqlTypes.isIntegral(integralCode) && SqlTypes.isNumericOrDecimal(numericCode);
    }
}
