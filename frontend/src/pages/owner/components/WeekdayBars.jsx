import { formatMoney } from '@/utils/reservationFormat';
import { WEEKDAY_LABEL, countWeekdays, perDay } from '@/utils/ownerDashboardFormat';
import styles from './WeekdayBars.module.css';

const WEEKEND = new Set(['SATURDAY', 'SUNDAY']);

/**
 * 요일별 확정 예약. 기간 합계 대신 "하루 평균"으로 보여 줘 기간이 길어도 감이 오게 한다.
 * 기간 안에 요일마다 날짜 수가 다르므로(예: 금요일 5번, 화요일 4번) 막대도 하루 평균으로 그린다.
 * 위에는 가장 바쁜·한가한 요일과 주말 매출 비중을 요약한다.
 *
 * @param {{ dayOfWeek: string, count: number, amount: number }[]} weekdays 월~일 7개
 * @param {string} from 조회 기간 시작 YYYY-MM-DD
 * @param {string} to 조회 기간 끝 YYYY-MM-DD
 */
export default function WeekdayBars({ weekdays, from, to }) {
  const dayCounts = countWeekdays(from, to);
  const rows = weekdays.map((item) => ({
    ...item,
    perDayCount: perDay(item.count, dayCounts[item.dayOfWeek]),
    perDayAmount: Math.round(perDay(item.amount, dayCounts[item.dayOfWeek])),
  }));
  const max = Math.max(...rows.map((item) => item.perDayCount), 0.1);
  // 기간 안에 한 번도 없는 요일(짧은 기간)은 바쁜·한가한 요일 비교에서 뺀다
  const present = rows.filter((item) => dayCounts[item.dayOfWeek] > 0);
  const busiest = present.reduce((best, item) =>
    item.perDayCount > best.perDayCount ? item : best,
  );
  const quietest = present.reduce((best, item) =>
    item.perDayCount < best.perDayCount ? item : best,
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
          <dd>
            <strong>{WEEKDAY_LABEL[busiest.dayOfWeek]}요일</strong>
            <span>하루 {busiest.perDayCount}건</span>
          </dd>
        </div>
        <div>
          <dt>가장 한가한 요일</dt>
          <dd>
            <strong>{WEEKDAY_LABEL[quietest.dayOfWeek]}요일</strong>
            <span>하루 {quietest.perDayCount}건</span>
          </dd>
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
              하루 <strong>{item.perDayCount}건</strong> · {formatMoney(item.perDayAmount)}
            </span>
          </li>
        ))}
      </ul>
      <p className={styles.hint}>하루 평균 = 기간 안 예약 ÷ 그 요일이 기간에 있던 날 수</p>
    </div>
  );
}
