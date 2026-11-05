import { formatMoney } from '@/utils/reservationFormat';
import { WEEKDAY_LABEL, perDay } from '@/utils/ownerDashboardFormat';
import styles from './WeekdayBars.module.css';

const WEEKEND = new Set(['SATURDAY', 'SUNDAY']);

/**
 * 요일별 확정 예약. 기간 합계 대신 "영업일 하루 평균"으로 보여 줘 기간이 길어도 감이 오게 한다.
 * 요일마다 영업한 날 수가 다르므로(예: 금요일 5번, 화요일은 임시 휴무로 3번) 막대도 하루 평균으로 그린다.
 * 위에는 가장 바쁜·한가한 요일과 주말 매출 비중을 요약한다.
 *
 * @param {{ dayOfWeek: string, count: number, amount: number, openDays: number }[]} weekdays 월~일 7개.
 *   openDays 는 서버가 휴무일을 빼고 센 영업한 날 수
 */
export default function WeekdayBars({ weekdays }) {
  const rows = weekdays.map((item) => ({
    ...item,
    perDayCount: perDay(item.count, item.openDays),
    perDayAmount: Math.round(perDay(item.amount, item.openDays)),
  }));
  const max = Math.max(...rows.map((item) => item.perDayCount), 0.1);
  // 기간 안에 영업한 날이 없는 요일(정기 휴무, 짧은 기간)은 바쁜·한가한 요일 비교에서 뺀다
  const open = rows.filter((item) => item.openDays > 0);
  const busiest = open.reduce(
    (best, item) => (!best || item.perDayCount > best.perDayCount ? item : best),
    null,
  );
  const quietest = open.reduce(
    (best, item) => (!best || item.perDayCount < best.perDayCount ? item : best),
    null,
  );
  const summaryOf = (item) =>
    item ? (
      <dd>
        <strong>{WEEKDAY_LABEL[item.dayOfWeek]}요일</strong>
        <span>하루 {item.perDayCount}건</span>
      </dd>
    ) : (
      <dd>
        <strong>-</strong>
        <span>영업일 없음</span>
      </dd>
    );
  const totalAmount = rows.reduce((sum, item) => sum + item.amount, 0);
  const weekendAmount = rows
    .filter((item) => WEEKEND.has(item.dayOfWeek))
    .reduce((sum, item) => sum + item.amount, 0);
  const weekendShare = totalAmount ? Math.round((weekendAmount / totalAmount) * 100) : null;

  return (
    <div className="b-stack">
      <dl className={styles.summary}>
        <div>
          <dt>가장 바쁜 요일</dt>
          {summaryOf(busiest)}
        </div>
        <div>
          <dt>가장 한가한 요일</dt>
          {summaryOf(quietest)}
        </div>
        <div>
          <dt>주말 매출 비중</dt>
          <dd>
            <strong>{weekendShare === null ? '-' : `${weekendShare}%`}</strong>
            <span>토·일</span>
          </dd>
        </div>
      </dl>
      <ul className={styles.list}>
        {rows.map((item) => (
          <li key={item.dayOfWeek}>
            <span className={styles.day}>{WEEKDAY_LABEL[item.dayOfWeek]}</span>
            <span className={styles.track} aria-hidden="true">
              <span
                className={
                  item === busiest && item.count > 0 ? `${styles.bar} ${styles.peak}` : styles.bar
                }
                style={{ width: `${(item.perDayCount / max) * 100}%` }}
              />
            </span>
            <span className={styles.value}>
              {item.openDays > 0 ? (
                <>
                  하루 <strong>{item.perDayCount}건</strong> · {formatMoney(item.perDayAmount)}
                </>
              ) : (
                '휴무'
              )}
            </span>
          </li>
        ))}
      </ul>
      <p className={styles.hint}>하루 평균 = 기간 안 예약 ÷ 그 요일에 영업한 날 수 (휴무일 제외)</p>
    </div>
  );
}
