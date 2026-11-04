// 백엔드 예약·결제·취소 상태 값 → 화면 표시. 배지 색은 디자인 CSS 클래스(b-green, b-red, b-neutral, b-dark)를 쓴다.
const RESERVATION_STATUS = {
  HOLD: { label: '결제 대기', tone: '' },
  CONFIRMED: { label: '예약 확정', tone: 'b-green' },
  CANCEL_PENDING: { label: '취소 처리 중', tone: '' },
  CANCELLED: { label: '취소 완료', tone: 'b-red' },
  EXPIRED: { label: '점유 만료', tone: 'b-neutral' },
};

// 시작일이 지난 확정 예약. 서버 상태는 CONFIRMED 그대로이고 화면에서만 이렇게 보여준다
export const PAST_RESERVATION_STATUS = { label: '이용 완료', tone: 'b-neutral' };

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

// 매장이 노쇼로 표시한 확정 예약. 서버 상태는 CONFIRMED 그대로이고 화면에서만 이렇게 보여준다
const NO_SHOW_STATUS = { label: '노쇼', tone: 'b-dark' };

// 매장 화면용. 예약 요약(목록 항목·상세 summary)을 받아 노쇼 표시가 있으면 노쇼로 보여준다
export const ownerReservationStatusOf = (reservation) =>
  reservation.noShow ? NO_SHOW_STATUS : reservationStatusOf(reservation.status);
