package com.ucamp.app.common.policy;

/**
 * 시간대 열기·닫기(예외 일정) 유형. 같은 시간에 열기와 닫기가 겹치면 닫기가 우선한다.
 */
public enum ScheduleExceptionType {

    /** 영업시간 외 시간을 추가로 연다 (연장 영업) */
    OPEN,
    /** 영업시간 안의 시간을 닫는다 (점심·임시 휴무, 사장님 사유 취소 후 닫기) */
    CLOSE
}
