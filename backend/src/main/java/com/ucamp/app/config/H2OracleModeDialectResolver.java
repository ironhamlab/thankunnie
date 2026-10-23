package com.ucamp.app.config;

import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;

/**
 * 연결된 DB 가 H2 일 때만 H2OracleModeDialect 를 쓰게 한다. 그 외(Oracle)는 Hibernate 기본 방언을 쓴다.
 * application.yml 의 hibernate.dialect_resolvers 로 등록한다.
 */
public class H2OracleModeDialectResolver implements DialectResolver {

    private static final String H2_DATABASE_NAME = "H2";

    @Override
    public Dialect resolveDialect(DialectResolutionInfo info) {
        if (H2_DATABASE_NAME.equals(info.getDatabaseName())) {
            return new H2OracleModeDialect(info);
        }
        return null;
    }
}
