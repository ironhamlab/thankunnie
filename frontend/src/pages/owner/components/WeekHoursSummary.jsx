import Panel from '@/components/common/Panel';
import { DAY_LABELS } from '@/utils/scheduleSlots';
import { toClock, toMinutes } from '@/utils/vacancyPreview';
import styles from './WeekHoursSummary.module.css';

const MINUTES_PER_DAY = 24 * 60;
const TICKS = [0, 6, 12, 18, 24];

const percent = (minutes) => `${(minutes / MINUTES_PER_DAY) * 100}%`;
const clockLabel = (time) => `${toClock(time).period} ${toClock(time).label}`;

/**
 * 고치는 중인 요일별 영업시간을 하루 0~24시 막대로 보여준다. 저장 전 값이 바로 반영된다.
 */
export default function WeekHoursSummary({ days }) {
  const openDays = days.filter((day) => !day.closed && day.closeTime > day.openTime);
  const totalMinutes = openDays.reduce(
    (sum, day) => sum + toMinutes(day.closeTime) - toMinutes(day.openTime),
    0,
  );
  const totalHours = Math.round((totalMinutes / 60) * 10) / 10;

  return (
    <Panel as="aside" padded className={styles.root} aria-label="주간 영업시간 한눈에 보기">
      <h2>한눈에 보기</h2>
      <p className={styles.total}>
        주 <strong>{openDays.length}일</strong> 영업 · <strong>{totalHours}시간</strong>
      </p>
      <ul className={styles.week}>
        {days.map((day) => {
          const open = openDays.includes(day);
          const start = open ? toMinutes(day.openTime) : 0;
          const end = open ? toMinutes(day.closeTime) : 0;
          return (
            <li key={day.dayOfWeek}>
              <span className={styles.label}>{DAY_LABELS[day.dayOfWeek]}</span>
              <span
                className={styles.track}
                aria-label={
                  open
                    ? `${DAY_LABELS[day.dayOfWeek]}요일 ${clockLabel(day.openTime)}부터 ${clockLabel(day.closeTime)}까지`
                    : `${DAY_LABELS[day.dayOfWeek]}요일 휴무`
                }
              >
                {open ? (
                  <span
                    className={styles.bar}
                    style={{ left: percent(start), width: percent(end - start) }}
                  />
                ) : (
                  <span className={styles.off}>휴무</span>
                )}
              </span>
            </li>
          );
        })}
      </ul>
      <div className={styles.ticks} aria-hidden="true">
        {TICKS.map((hour) => (
          <span key={hour} style={{ left: percent(hour * 60) }}>
            {hour}
          </span>
        ))}
      </div>
    </Panel>
  );
}
