import { addDays, todayKst } from '@/utils/scheduleDate';
import { formatDayLabel } from '@/utils/scheduleSlots';
import styles from './DateShortcuts.module.css';

const SHORTCUTS = [
  ['오늘', 0],
  ['내일', 1],
  ['모레', 2],
];

/**
 * 오늘·내일·모레 빠른 선택과 달력. 열기·닫기 창과 빈자리 열기 화면이 같이 쓴다.
 */
export default function DateShortcuts({ value, min, max, onChange }) {
  const today = todayKst();
  return (
    <div className={styles.root}>
      {SHORTCUTS.map(([label, days]) => {
        const date = addDays(today, days);
        return (
          <button
            key={label}
            type="button"
            aria-pressed={value === date}
            onClick={() => onChange(date)}
          >
            {label} <small>{formatDayLabel(date)}</small>
          </button>
        );
      })}
      <input
        type="date"
        required
        min={min}
        max={max}
        value={value}
        aria-label="날짜 직접 선택"
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}
