import { formatMoney } from '@/utils/reservationFormat';
import { WEEKDAY_LABEL } from '@/utils/ownerDashboardFormat';
import styles from './WeekdayBars.module.css';

/**
 * 요일별 확정 예약 가로 막대. 가장 많은 요일을 진하게 칠한다.
 *
 * @param {{ dayOfWeek: string, count: number, amount: number }[]} weekdays 월~일 7개
 */
export default function WeekdayBars({ weekdays }) {
  const max = Math.max(...weekdays.map((item) => item.count), 1);
  return (
    <ul className={styles.list}>
      {weekdays.map((item) => (
        <li key={item.dayOfWeek}>
          <span className={styles.day}>{WEEKDAY_LABEL[item.dayOfWeek]}</span>
          <span className={styles.track} aria-hidden="true">
            <span
              className={
                item.count === max && item.count > 0 ? `${styles.bar} ${styles.peak}` : styles.bar
              }
              style={{ width: `${(item.count / max) * 100}%` }}
            />
          </span>
          <span className={styles.value}>
            {item.count}건 · {formatMoney(item.amount)}
          </span>
        </li>
      ))}
    </ul>
  );
}
