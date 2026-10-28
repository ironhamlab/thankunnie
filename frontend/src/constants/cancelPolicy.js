// 고객 취소 환불 정책 (CNL-02~04). 실제 환불액은 서버 견적이 기준이며, 여기 값은 구간 안내 표시용이다
export const REFUND_RATE_TIERS = [
  { rate: 100, label: '이용 2일 전까지' },
  { rate: 50, label: '이용 1일 전' },
  { rate: 0, label: '이용 당일' },
];

// 무료 취소 예외(결제 후 10분 이내 전액 환불) 판정 결과별 안내 (백엔드 GraceCancelResult)
export const GRACE_CANCEL_MESSAGE = {
  APPLIED: '결제 후 10분 이내라 무료 취소가 적용되어 전액 환불돼요. 오늘 무료 취소 1회가 차감돼요.',
  NOT_NEEDED: '기본 정책으로 전액 환불되어 무료 취소 횟수는 차감되지 않아요.',
  LAST_MINUTE_HOLD: '시작 3시간 이내에 잡은 초임박 예약은 무료 취소 대상이 아니에요.',
  WINDOW_PASSED: '결제 후 10분이 지나 기본 환불 정책이 적용돼요.',
  ALREADY_USED_TODAY: '오늘 무료 취소를 이미 사용해 기본 환불 정책이 적용돼요.',
};
