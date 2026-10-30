// 빈자리 열기 화면의 미리보기용 정책 값. 서버 BookingPolicy 와 같은 값이며, 등록 가능 여부와 가격은 서버가 다시 확인한다.
export const VACANCY_POLICY = {
  TIME_UNIT_MINUTES: 30,
  // 열기는 시작 30분 전까지만 등록할 수 있다
  OPEN_DEADLINE_MINUTES: 30,
  // 오늘 포함 60일 안에서만 등록할 수 있다
  BOOKING_MAX_DAYS: 60,
  // 시작 3시간 전부터 초임박가, 8시간 전부터 임박 구간 (임박 구간 가격은 판매가와 같다)
  LAST_MINUTE_HOURS: 3,
  IMMINENT_HOURS: 8,
  MAX_DEPOSIT: 20000,
};
