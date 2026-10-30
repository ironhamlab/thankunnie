import { money } from '@/utils/demoData';
import { PAYMENT_STATUS } from '@/constants/reservationStatus';
import { datePart, timePart } from '@/utils/scheduleDate';
import Badge from '@/components/common/Badge';
import Notice from '@/components/common/Notice';
import styles from './BookingBreakdown.module.css';

/**
 * 결제 및 정산 요약. 금액은 예약 상세 응답(summary)의 서버 값, 결제 상태는 최근 예약금 결제(payment)로 보여준다.
 */
export default function BookingBreakdown({ summary, payment, compact = false }) {
  const paidAmount = payment?.amount ?? summary.deposit;
  return (
    <section
      className={`b-panel b-pad ${styles.settlement} ` + (compact ? 'b-settlement-compact' : '')}
    >
      <div className="b-row">
        <h2>결제 및 정산 요약</h2>
      </div>
      <dl className="b-details">
        <div>
          <dt>
            <strong>총 시술 금액 (확정가)</strong>
          </dt>
          <dd className="b-pink">{money(summary.appliedPrice)}</dd>
        </div>
      </dl>
      <div className="b-two-grid">
        <div className="b-highlight">
          <small>
            결제한 예약금{' '}
            {payment && <Badge>{PAYMENT_STATUS[payment.status] ?? payment.status}</Badge>}
          </small>
          <strong className="b-pink">{money(paidAmount)}</strong>
          {payment?.approvedAt && (
            <small>
              결제 시각 {datePart(payment.approvedAt)} {timePart(payment.approvedAt)}
            </small>
          )}
        </div>
        <Notice>
          <small>매장 방문 현장 잔금</small>
          <strong>{money(summary.balance)}</strong>
          <small>시술 종료 후 매장에서 직접 결제</small>
        </Notice>
      </div>
    </section>
  );
}
