import { useState } from 'react';
import { addDays } from '@/utils/scheduleDate';
import styles from '../ShopDetailPage.module.css';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const monthOf = (date) => date.slice(0, 7);
const shiftMonth = (month, offset) => {
  const [year, value] = month.split('-').map(Number);
  return new Date(Date.UTC(year, value - 1 + offset, 1)).toISOString().slice(0, 7);
};

// 일요일부터 시작하는 달력 칸. 앞쪽 빈칸은 null
const monthCells = (month) => {
  const first = `${month}-01`;
  const leading = new Date(`${first}T00:00:00Z`).getUTCDay();
  const cells = Array.from({ length: leading }, () => null);
  for (let date = first; monthOf(date) === month; date = addDays(date, 1)) {
    cells.push(date);
  }
  return cells;
};

/**
 * 예약 날짜 달력. firstDate~lastDate 밖의 날짜는 고를 수 없다.
 * 휴무 요일도 매장이 따로 연 시간이 있을 수 있어 고를 수는 있게 두고 표시만 한다.
 */
export default function BookingCalendar({ firstDate, lastDate, selectedDate, isClosed, onSelect }) {
  const [month, setMonth] = useState(() => monthOf(selectedDate ?? firstDate));
  const [year, monthValue] = month.split('-').map(Number);
  const canPrev = month > monthOf(firstDate);
  const canNext = month < monthOf(lastDate);

  return (
    <div className={styles.calendar}>
      <div className={styles.calendarHeader}>
        <button
          type="button"
          onClick={() => setMonth(shiftMonth(month, -1))}
          disabled={!canPrev}
          aria-label="이전 달"
        >
          ‹
        </button>
        <strong aria-live="polite">
          {year}년 {monthValue}월
        </strong>
        <button
          type="button"
          onClick={() => setMonth(shiftMonth(month, 1))}
          disabled={!canNext}
          aria-label="다음 달"
        >
          ›
        </button>
      </div>
      <div className={styles.calendarGrid} role="group" aria-label="예약 날짜">
        {WEEKDAYS.map((weekday) => (
          <span key={weekday} className={styles.weekday}>
            {weekday}
          </span>
        ))}
        {monthCells(month).map((date, index) => {
          if (!date) return <span key={`blank-${index}`} />;
          const outOfRange = date < firstDate || date > lastDate;
          const closed = !outOfRange && isClosed(date);
          const selected = date === selectedDate;
          const classes = [
            selected && 'is-selected',
            closed && styles.closedDay,
            date === firstDate && styles.today,
          ]
            .filter(Boolean)
            .join(' ');
          return (
            <button
              key={date}
              type="button"
              className={classes || undefined}
              disabled={outOfRange}
              aria-pressed={selected}
              aria-label={`${date}${closed ? ' 휴무' : ''}`}
              onClick={() => onSelect(date)}
            >
              {Number(date.slice(8))}
              {closed && <small>휴무</small>}
            </button>
          );
        })}
      </div>
    </div>
  );
}
