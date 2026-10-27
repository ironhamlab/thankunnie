import { money } from '@/utils/demoData';
import { REFUND_TIERS, quoteRefund } from '@/utils/demoRefundPolicy';

const describeDays = (daysLeft) => {
  if (daysLeft === null) return '취소 당시 적용된 구간';
  if (daysLeft <= 0) return '오늘 이용 예정';
  return `이용일까지 ${daysLeft}일 남음`;
};

/**
 * 환불 정책 구간과 현재 예약에 적용되는 예상 환불액
 */
export default function RefundPolicyCard({ booking }) {
  const quote = quoteRefund(booking);
  const isCancelled = booking.status === 'cancelled';
  const refundRate = quote.paidAmount
    ? Math.round((quote.refundAmount / quote.paidAmount) * 100)
    : 0;

  return (
    <div className="b-refund-card">
      <div className="b-row">
        <span className="b-badge b-neutral">{describeDays(quote.daysLeft)}</span>
        <small className="b-muted">납부 예약금 한도 내 환불</small>
      </div>

      <ul className="b-refund-tiers">
        {REFUND_TIERS.map((tier) => (
          <li key={tier.key} className={tier.key === quote.tier.key ? 'is-current' : ''}>
            <span>{tier.label}</span>
            <strong>{money(tier.amount)}</strong>
            {tier.key === quote.tier.key && <em>{isCancelled ? '적용됨' : '현재 구간'}</em>}
          </li>
        ))}
      </ul>

      <div className="b-refund-result">
        <div>
          <span>{isCancelled ? '환불 금액' : '지금 취소하면 환불'}</span>
          <strong className="b-pink">{money(quote.refundAmount)}</strong>
        </div>
        <div className="b-refund-split" role="img" aria-label={`환불 ${refundRate}%`}>
          <span className="is-refund" style={{ width: `${refundRate}%` }} />
          <span className="is-kept" style={{ width: `${100 - refundRate}%` }} />
        </div>
        <dl className="b-refund-breakdown">
          <div>
            <dt>납부 예약금</dt>
            <dd>{money(quote.paidAmount)}</dd>
          </div>
          <div>
            <dt>환불 예정</dt>
            <dd className="b-green-text">- {money(quote.refundAmount)}</dd>
          </div>
          <div>
            <dt>환불 불가 (취소 수수료)</dt>
            <dd>{money(quote.nonRefundableAmount)}</dd>
          </div>
        </dl>
      </div>
      <small className="b-help">
        모의 환불이며 실제 금융 거래가 발생하지 않습니다. 실제 서비스에서는 서버가 취소 시각
        기준으로 환불액을 다시 계산합니다.
      </small>
    </div>
  );
}
