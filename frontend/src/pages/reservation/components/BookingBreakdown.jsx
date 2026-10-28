import { money } from '@/utils/demoData';
import Badge from '@/components/common/Badge';
import styles from './BookingBreakdown.module.css';

export default function BookingBreakdown({ service, booking, compact = false }) {
  const discount = Math.round((1 - booking.price / service.original) * 100);
  return (
    <section
      className={`b-panel b-pad ${styles.settlement} ` + (compact ? 'b-settlement-compact' : '')}
    >
      <div className="b-row">
        <h2>결제 및 정산 요약</h2>
        <Badge>타임세일 할인 {discount}% 적용</Badge>
      </div>
      <dl className="b-details">
        <div>
          <dt>정상 시술 금액</dt>
          <dd>
            <del>{money(service.original)}</del>
          </dd>
        </div>
        <div className="b-pink">
          <dt>타임세일 할인금액 (-{discount}%)</dt>
          <dd>-{money(service.original - booking.price)}</dd>
        </div>
        <div>
          <dt>
            <strong>총 시술 금액 (확정가)</strong>
          </dt>
          <dd className="b-pink">{money(booking.price)}</dd>
        </div>
      </dl>
      <div className="b-two-grid">
        <div className="b-highlight">
          <small>
            모의 결제 완료 예약금 <Badge>완료</Badge>
          </small>
          <strong className="b-pink">{money(booking.deposit)}</strong>
          <small>실제 과금 없는 모의 테스트 승인 (20%)</small>
        </div>
        <div className="b-notice">
          <small>매장 방문 현장 잔금</small>
          <strong>{money(booking.price - booking.deposit)}</strong>
          <small>시술 종료 후 매장에서 직접 결제</small>
        </div>
      </div>
    </section>
  );
}
