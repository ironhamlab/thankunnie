// 빈자리 열기·닫기 구분별 화면 문구와 사유 빠른 선택
export const EXCEPTION_TYPES = {
  CLOSE: {
    title: '시간 닫기',
    verb: '닫기',
    description: '점심·개인 일정·임시 휴무. 이 시간엔 예약을 받지 않아요.',
    reasons: ['점심시간', '개인 일정', '임시 휴무'],
  },
  OPEN: {
    title: '빈자리 열기',
    verb: '빈자리 열기',
    description: '손님을 더 받을 시간. 고객 빈자리 목록에 올라가요.',
    reasons: ['연장 영업', '당일 취소 자리', '이벤트'],
  },
};

// 이 화면은 하루 안 일정만 만들고 고친다. 다른 경로로 생긴 여러 날 일정을 하루로 줄여 저장하지 않게 막을 때 쓴다
export const MULTI_DAY_EDIT_BLOCKED =
  '여러 날에 걸친 일정은 여기서 수정할 수 없어요. 삭제한 뒤 다시 등록해 주세요.';
