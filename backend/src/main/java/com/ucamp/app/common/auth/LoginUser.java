package com.ucamp.app.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드의 AuthUser 파라미터에 현재 사용자를 주입한다.
 * required 가 true(기본)이면 비로그인 요청에 UNAUTHORIZED, false 면 null 을 주입한다.
 *
 *
 * (@LoginUser AuthUser loginUser)
 *
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {

    boolean required() default true;
}
