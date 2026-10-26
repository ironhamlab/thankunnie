import { DEMO_DATE, getPayment } from './data';

const DAY_MS = 24 * 60 * 60 * 1000;

// 확정 환불 정책: 이용 2일 전까지 20,000원 / 1일 전 10,000원 / 당일 0원 (납부 예약금 한도).
// 시연용 계산이며 실제 환불액은 서버(예약 취소)가 현재 시각 기준으로 계산한다.
export const REFUND_TIERS = [
  { key: 'twoDays', label: '이용 2일 전까지', minDays: 2, amount: 20000 },
  { key: 'oneDay', label: '이용 1일 전', minDays: 1, amount: 10000 },
  { key: 'sameDay', label: '이용 당일', minDays: 0, amount: 0 },
];

export const CANCEL_REASONS = [
  '개인 일정 변경',
  '다른 날짜로 다시 예약할게요',
  '매장 위치·시간이 맞지 않아요',
  '기타',
];

/** 시연 기준일부터 이용일까지 남은 날짜 수 (한국 날짜 기준) */
export function daysUntil(date, today = DEMO_DATE) {
  return Math.round((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / DAY_MS);
}

export function findTier(key) {
  return REFUND_TIERS.find((tier) => tier.key === key) ?? REFUND_TIERS[REFUND_TIERS.length - 1];
}

/**
 * 예약 취소 시 예상 환불액. 이미 취소된 예약은 취소 당시 기록을 그대로 보여준다.
 */
export function quoteRefund(booking) {
  const payment = getPayment(booking);
  const remainingAmount = payment.amount - payment.refundedAmount;

  if (booking.cancellation) {
    const refundAmount = booking.refund ?? booking.cancellation.policyRefundAmount;
    return {
      tier: findTier(booking.cancellation.tierKey),
      daysLeft: null,
      paidAmount: payment.amount,
      refundAmount,
      nonRefundableAmount: payment.amount - refundAmount,
    };
  }

  const daysLeft = daysUntil(booking.date);
  const tier = REFUND_TIERS.find((item) => daysLeft >= item.minDays) ?? findTier('sameDay');
  const refundAmount = Math.min(tier.amount, remainingAmount);
  return {
    tier,
    daysLeft,
    paidAmount: payment.amount,
    refundAmount,
    nonRefundableAmount: payment.amount - refundAmount,
  };
}
