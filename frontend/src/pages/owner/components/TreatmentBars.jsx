import { formatMoney } from '@/utils/reservationFormat';
import { formatRate } from '@/utils/ownerDashboardFormat';
import styles from './TreatmentBars.module.css';

/**
 * 시술별 확정 예약 금액 가로 막대. 금액·건수·비중은 막대 옆에 글자로 함께 적는다.
 *
 * @param {{ treatmentId: number, treatmentName: string, count: number, amount: number, share: number|null }[]} treatments 금액 많은 순
 */
export default function TreatmentBars({ treatments }) {
  const max = Math.max(...treatments.map((item) => item.amount), 1);
  return (
    <ul className={styles.list}>
      {treatments.map((item) => (
        <li key={item.treatmentId}>
          <span className={styles.name}>{item.treatmentName}</span>
          <span className={styles.track} aria-hidden="true">
            <span className={styles.bar} style={{ width: `${(item.amount / max) * 100}%` }} />
          </span>
          <span className={styles.value}>
            <strong>{formatRate(item.share)}</strong> · {formatMoney(item.amount)} · {item.count}건
          </span>
        </li>
      ))}
    </ul>
  );
}
