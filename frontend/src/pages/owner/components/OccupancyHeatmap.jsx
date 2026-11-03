import { WEEKDAYS, WEEKDAY_LABEL, formatMinutes } from '@/utils/ownerDashboardFormat';
import styles from './OccupancyHeatmap.module.css';

// 진하기 단계. 0 은 비어 있음
const LEVELS = 4;

/**
 * 요일 × 시간대 예약 점유 히트맵. 예약 시간을 정시 칸으로 나눠 더한 값이라 긴 시술도 지나가는 시간대마다 바쁘게 보인다.
 * 시간 범위는 예약이 있었던 가장 이른 시~가장 늦은 시로 정한다.
 *
 * @param {{ dayOfWeek: string, hour: number, bookedMinutes: number }[]} cells 0 인 칸은 없다
 */
export default function OccupancyHeatmap({ cells }) {
  const byKey = new Map(
    cells.map((cell) => [`${cell.dayOfWeek}-${cell.hour}`, cell.bookedMinutes]),
  );
  const hours = cells.map((cell) => cell.hour);
  const firstHour = Math.min(...hours);
  const lastHour = Math.max(...hours);
  const max = Math.max(...cells.map((cell) => cell.bookedMinutes), 1);
  const levelOf = (minutes) => (minutes ? Math.max(1, Math.ceil((minutes / max) * LEVELS)) : 0);
  const rows = Array.from({ length: lastHour - firstHour + 1 }, (_, index) => firstHour + index);
  const peak = cells.reduce(
    (best, cell) => (cell.bookedMinutes > (best?.bookedMinutes ?? 0) ? cell : best),
    null,
  );

  return (
    <div className="b-stack">
      {peak && (
        <p className={styles.peak}>
          가장 바쁜 시간{' '}
          <strong>
            {WEEKDAY_LABEL[peak.dayOfWeek]}요일 {peak.hour}~{peak.hour + 1}시
          </strong>
        </p>
      )}
      <div className={styles.scroll}>
        {/* 화면 읽기 프로그램에는 칸마다 요일·시간·점유 시간을 읽어 준다 */}
        <div className={styles.grid} role="list" aria-label="요일·시간대별 예약이 차 있던 시간">
          <span aria-hidden="true" />
          {WEEKDAYS.map((day) => (
            <span key={day} className={styles.head} aria-hidden="true">
              {WEEKDAY_LABEL[day]}
            </span>
          ))}
          {rows.map((hour) => [
            <span key={`h-${hour}`} className={styles.hour} aria-hidden="true">
              {hour}시
            </span>,
            ...WEEKDAYS.map((day) => {
              const minutes = byKey.get(`${day}-${hour}`) ?? 0;
              const text = `${WEEKDAY_LABEL[day]}요일 ${hour}시 ${formatMinutes(minutes)}`;
              return (
                <span
                  key={`${day}-${hour}`}
                  role="listitem"
                  className={`${styles.cell} ${styles[`level${levelOf(minutes)}`]}`}
                  title={text}
                >
                  <span className="b-visually-hidden">{text}</span>
                </span>
              );
            }),
          ])}
        </div>
      </div>
      <div className={styles.legend} aria-hidden="true">
        <span>한가함</span>
        {Array.from({ length: LEVELS }, (_, index) => (
          <i key={index} className={styles[`level${index + 1}`]} />
        ))}
        <span>바쁨</span>
      </div>
    </div>
  );
}
