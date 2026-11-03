// 백엔드 예약·결제·취소 상태 값 → 화면 표시. 배지 색은 디자인 CSS 클래스(b-green, b-red, b-neutral)를 쓴다.
export const RESERVATION_STATUS = {
  HOLD: { label: '결제 대기', tone: '' },
  CONFIRMED: { label: '예약 확정', tone: 'b-green' },
  CANCEL_PENDING: { label: '취소 처리 중', tone: '' },
  CANCELLED: { label: '취소 완료', tone: 'b-red' },
  EXPIRED: { label: '점유 만료', tone: 'b-neutral' },
};

export const PAYMENT_STATUS = {
  READY: '결제 대기',
  PAID: '결제 완료',
  FAILED: '결제 실패',
  UNKNOWN: '결제 확인 중',
  REFUND_PENDING: '환불 처리 중',
  PART_REFUNDED: '부분 환불',
  REFUNDED: '환불 완료',
};

export const CANCEL_STATUS = {
  REQUESTED: '취소 접수',
  COMPLETED: '취소 완료',
  FAILED: '취소 실패',
  UNKNOWN: '환불 확인 중',
};

export const CANCEL_TYPE = {
  CUSTOMER: '고객 취소',
  SHOP_FAULT: '매장 사유 취소',
  CUSTOMER_REQUEST: '고객 요청 대리 취소',
};

export const reservationStatusOf = (status) =>
  RESERVATION_STATUS[status] ?? { label: status, tone: 'b-neutral' };
