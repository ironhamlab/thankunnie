import { useEffect, useRef, useState } from 'react';
import { addDays } from '@/utils/scheduleDate';
import { dayLabel } from '@/utils/exploreVacancy';
import styles from './DateCalendar.module.css';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// YYYY-MM 의 1일 요일과 날짜 수
function monthInfo(month) {
  const [year, mm] = month.split('-').map(Number);
  return {
    firstWeekday: new Date(Date.UTC(year, mm - 1, 1)).getUTCDay(),
    days: new Date(Date.UTC(year, mm, 0)).getUTCDate(),
  };
}

// 버튼에 날짜와 함께 붙일 말. 사흘 안이면 오늘·내일·모레, 그 밖에는 요일
function relativeLabel(date, today) {
  const label = dayLabel(date, today);
  return /^\d/.test(label) ? label.slice(-3) : label;
}

function shiftMonth(month, step) {
  const [year, mm] = month.split('-').map(Number);
  return new Date(Date.UTC(year, mm - 1 + step, 1)).toISOString().slice(0, 7);
}

/**
 * 날짜 하나를 보여주고, 누르면 달력을 펼쳐 min~max 안에서 하루를 고른다. 날짜는 YYYY-MM-DD(한국 시간)다.
 */
export default function DateCalendar({ value, min, max, onChange, disabled = false }) {
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(value.slice(0, 7));
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const close = (event) => {
      if (
        event.type === 'keydown' ? event.key === 'Escape' : !rootRef.current?.contains(event.target)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [open]);

  const toggle = () => {
    setMonth(value.slice(0, 7));
    setOpen((current) => !current);
  };
  const choose = (date) => {
    onChange(date);
    setOpen(false);
  };
  // 달력이 열린 채로 화살표로 달을 넘겨도 고른 날짜가 보이게 표시 중인 달도 함께 옮긴다
  const step = (days) => {
    const next = addDays(value, days);
    if (next < min || next > max) return;
    setMonth(next.slice(0, 7));
    onChange(next);
  };

  const { firstWeekday, days } = monthInfo(month);
  const cells = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: days }, (_, index) => `${month}-${String(index + 1).padStart(2, '0')}`),
  ];
  return (
    <div className={styles.root} ref={rootRef}>
      <div className={styles.picker}>
        <button
          type="button"
          className={styles.arrow}
          aria-label="전날"
          disabled={disabled || value <= min}
          onClick={() => step(-1)}
        >
          ‹
        </button>
        <button
          type="button"
          className={styles.current}
          aria-haspopup="dialog"
          aria-expanded={open}
          disabled={disabled}
          onClick={toggle}
        >
          <span aria-hidden="true">▦</span>
          <strong>{value.slice(5).replace('-', '.')}</strong>
          <span>{relativeLabel(value, min)}</span>
        </button>
        <button
          type="button"
          className={styles.arrow}
          aria-label="다음날"
          disabled={disabled || value >= max}
          onClick={() => step(1)}
        >
          ›
        </button>
      </div>
      {open && (
        <div className={styles.popover} role="dialog" aria-label="날짜 선택">
          <div className={styles.monthBar}>
            <button
              type="button"
              aria-label="이전 달"
              disabled={month <= min.slice(0, 7)}
              onClick={() => setMonth(shiftMonth(month, -1))}
            >
              ‹
            </button>
            <strong>
              {Number(month.slice(0, 4))}년 {Number(month.slice(5))}월
            </strong>
            <button
              type="button"
              aria-label="다음 달"
              disabled={month >= max.slice(0, 7)}
              onClick={() => setMonth(shiftMonth(month, 1))}
            >
              ›
            </button>
          </div>
          <div className={styles.grid}>
            {WEEKDAYS.map((weekday) => (
              <span key={weekday} className={styles.weekday}>
                {weekday}
              </span>
            ))}
            {cells.map((date, index) =>
              date ? (
                <button
                  type="button"
                  key={date}
                  className={date === min ? styles.today : ''}
                  aria-pressed={date === value}
                  aria-label={date}
                  disabled={date < min || date > max}
                  onClick={() => choose(date)}
                >
                  {Number(date.slice(8))}
                </button>
              ) : (
                <span key={`blank-${index}`} />
              ),
            )}
          </div>
          <p className={styles.help}>오늘부터 60일 안에서 고를 수 있어요</p>
        </div>
      )}
    </div>
  );
}
