package com.beyond.wbs.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 감사 로그 기록 어노테이션.
 * Controller 메서드에 붙이면 AOP가 요청/응답을 자동 기록한다.
 *
 * action을 비워두면 HTTP Method에서 자동 추출:
 *   POST → 생성, GET → 조회, PATCH/PUT → 수정, DELETE → 삭제
 *
 * 명시적으로 비즈니스 행위를 지정할 수도 있음:
 *   @AuditLog(action = "승인")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String action() default "";
}
