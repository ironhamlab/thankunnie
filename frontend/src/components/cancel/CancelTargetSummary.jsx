import { reservationStatusOf } from '@/constants/reservationStatus';
import { formatMoney, formatReservationTime } from '@/utils/reservationFormat';
import Badge from '@/components/common/Badge';
import styles from './CancelTargetSummary.module.css';

/**
 * 취소 대상 예약 요약 (고객 취소·사장님 취소·취소 결과 공용). 예약 상세 응답의 summary 를 그대로 받는다.
 */
export default function CancelTargetSummary({ summary }) {
  const status = reservationStatusOf(summary.status);

  return (
    <div className={styles.root}>
      <div className="b-row">
        <small className="b-muted">예약번호 {summary.reservationId}</small>
        <Badge className={status.tone}>{status.label}</Badge>
      </div>
      <small>{summary.shopName}</small>
      <strong>{summary.treatmentName}</strong>
      <span className="b-pink">{formatReservationTime(summary.startsAt, summary.endsAt)}</span>
      <small className="b-muted">
        시술 금액 {formatMoney(summary.appliedPrice)} · 예약금 {formatMoney(summary.deposit)}
      </small>
    </div>
  );
}
