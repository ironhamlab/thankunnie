import { PAYMENT_STATUS, formatDateTime, getPayment, money } from '@/utils/demoData';
import styles from './PaymentStatusCard.module.css';

const buildTimeline = (booking, payment) => {
  const cancellation = booking.cancellation;
  const isPending = payment.status === 'REFUND_PENDING';
  const isRefundDone = ['REFUNDED', 'PART_REFUNDED'].includes(payment.status);
  const steps = [{ key: 'paid', label: '예약금 결제', at: payment.approvedAt, state: 'done' }];
  if (!cancellation) return steps;

  steps.push({ key: 'requested', label: '취소 접수', at: cancellation.requestedAt, state: 'done' });
  if (booking.refund === 0) {
    steps.push({ key: 'none', label: '환불 대상 아님 (당일 취소)', at: null, state: 'done' });
    return steps;
  }
  steps.push({
    key: 'pending',
    label: 'PG 환불 요청',
    at: isPending ? null : cancellation.requestedAt,
    state: isPending ? 'current' : 'done',
  });
  steps.push({
    key: 'refunded',
    label: payment.status === 'PART_REFUNDED' ? '부분 환불 완료' : '환불 완료',
    at: payment.refundedAt,
    state: isRefundDone ? 'done' : 'todo',
  });
  return steps;
};

/**
 * 예약금결제 상태·금액·이력을 한눈에 보여주는 카드
 */
export default function PaymentStatusCard({ booking }) {
  const payment = getPayment(booking);
  const status = PAYMENT_STATUS[payment.status] ?? PAYMENT_STATUS.PAID;
  const remainingAmount = payment.amount - payment.refundedAmount;
  const timeline = buildTimeline(booking, payment);
  const refundRate = payment.amount
    ? Math.round((payment.refundedAmount / payment.amount) * 100)
    : 0;

  return (
    <div className={styles.root}>
      <div className={styles.head}>
        <div>
          <small className="b-muted">예약금결제 #{payment.depositPaymentId}</small>
          <strong className={styles.method}>{payment.method}</strong>
        </div>
        <span className={`b-pay-status is-${status.tone}`}>
          {payment.status === 'REFUND_PENDING' && (
            <span className="b-pay-spinner" aria-hidden="true" />
          )}
          {status.label}
        </span>
      </div>

      <div className={styles.stats}>
        <div>
          <span>결제한 예약금</span>
          <strong>{money(payment.amount)}</strong>
        </div>
        <div className="is-refund">
          <span>환불된 금액</span>
          <strong>{money(payment.refundedAmount)}</strong>
        </div>
        <div>
          <span>남은 결제 금액</span>
          <strong>{money(remainingAmount)}</strong>
        </div>
      </div>

      <div className={styles.meter} role="img" aria-label={`예약금 중 ${refundRate}% 환불`}>
        <span style={{ width: `${refundRate}%` }} />
      </div>
      <div className={styles.meterLegend}>
        <span>환불 {refundRate}%</span>
        <span>결제 유지 {100 - refundRate}%</span>
      </div>

      <dl className={`b-details ${styles.meta}`}>
        <div>
          <dt>결제 번호 (imp_uid)</dt>
          <dd className={styles.mono}>{payment.impUid}</dd>
        </div>
        <div>
          <dt>결제 승인</dt>
          <dd>{formatDateTime(payment.approvedAt)}</dd>
        </div>
        {payment.refundedAt && (
          <div>
            <dt>환불 완료</dt>
            <dd>{formatDateTime(payment.refundedAt)}</dd>
          </div>
        )}
        {booking.cancellation && (
          <div>
            <dt>취소 사유</dt>
            <dd>{booking.cancellation.reason}</dd>
          </div>
        )}
      </dl>

      <ol className={styles.timeline}>
        {timeline.map((step) => (
          <li key={step.key} className={`is-${step.state}`}>
            <span className={styles.dot} aria-hidden="true" />
            <div>
              <strong>{step.label}</strong>
              <small>{step.state === 'current' ? '처리 중…' : formatDateTime(step.at)}</small>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}
