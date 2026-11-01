import { useState } from 'react';
import { TIME_OPTIONS, toClock, toMinutes, toTime } from '@/utils/vacancyPreview';
import styles from './TimeRangePicker.module.css';

// 영업하지 않는 날 접힌 상태에서 보여줄 첫 칸
const DEFAULT_FROM = '09:00';
// 열기·닫기는 하루 안에서 끝나야 해서 23:00 칸(~23:30)까지만 고른다
const LAST_START = '23:00';
const FIRST_START = '00:00';
// HH:mm 를 분만큼 옮긴다 (하루 범위 안)
const shift = (time, minutes) =>
  toTime(Math.min(Math.max(toMinutes(time) + minutes, 0), 23 * 60 + 30));
const STATUS_LABELS = {
  AVAILABLE: '비어 있음',
  BOOKED: '예약됨',
  CLOSED: '닫음',
  PASSED: '마감',
  OFF: '영업 외',
};
// 칸에는 글자를 넣지 않고 색으로만 구분하므로, 화면에 나온 상태만 범례로 알려준다
const LEGEND_STATUSES = ['CLOSED', 'BOOKED', 'OFF'];

/**
 * 30분 칸을 눌러 시간대를 고른다. 시작 칸을 누른 뒤 끝 칸을 누르면 그 사이가 선택된다.
 * 오전·오후로 나눠 12시간 표기로 보여주고, 칸의 상태(예약됨·닫음·영업 외)는 색과 범례로 구분한다.
 * 기본은 영업 범위 앞뒤 1시간만 보여주고, 영업시간 밖 열기를 위해 전체 시간으로 펼칠 수 있다.
 *
 * @param slots     useDaySlots 의 칸 상태 (영업 범위 밖 칸은 영업 외로 본다)
 * @param minStart  이보다 이른 칸은 고를 수 없다 (오늘의 등록 마감). 없으면 제한 없음
 */
export default function TimeRangePicker({ slots, startTime, endTime, minStart, onChange }) {
  const [picking, setPicking] = useState(false);
  const [showAll, setShowAll] = useState(false);
  const statusOf = new Map(slots.map((slot) => [slot.time, slot.status]));
  // 접었을 때는 영업 범위 앞뒤 1시간(영업이 없는 날은 9시부터), 펼치면 0시부터 보여준다. 고른 구간은 늘 보이게 한다
  const minTime = (a, b) => (a < b ? a : b);
  const maxTime = (a, b) => (a > b ? a : b);
  const nearFrom = slots.length ? shift(slots[0].time, -60) : DEFAULT_FROM;
  const nearTo = slots.length ? minTime(LAST_START, shift(slots.at(-1).time, 60)) : LAST_START;
  const from = showAll ? FIRST_START : minTime(nearFrom, startTime);
  const to = showAll ? LAST_START : maxTime(nearTo, shift(endTime, -30));
  const times = TIME_OPTIONS.filter((time) => time >= from && time <= to);
  const canExpand = showAll || from > FIRST_START || to < LAST_START;
  const legend = LEGEND_STATUSES.filter((status) =>
    times.some(
      (time) => statusOf.get(time) === status || (status === 'OFF' && !statusOf.has(time)),
    ),
  );

  const handleClick = (time) => {
    const slotEnd = toTime(toMinutes(time) + 30);
    if (picking && time >= startTime) {
      onChange({ startTime, endTime: slotEnd });
      setPicking(false);
    } else {
      onChange({ startTime: time, endTime: slotEnd });
      setPicking(true);
    }
  };

  return (
    <div className={styles.root}>
      <p className={styles.hint} aria-live="polite">
        {picking
          ? `${toClock(startTime).period} ${toClock(startTime).label}부터 · 끝나는 칸을 눌러 주세요`
          : '시작 칸을 누르고 끝나는 칸을 누르세요'}
      </p>
      {legend.length > 0 && (
        <ul className={styles.legend} aria-hidden="true">
          {legend.map((status) => (
            <li key={status}>
              <span className={`${styles.swatch} ${styles[status.toLowerCase()]}`} />
              {STATUS_LABELS[status]}
            </li>
          ))}
        </ul>
      )}
      <div role="group" aria-label="30분 칸 시간 선택">
        {['오전', '오후'].map((period) => {
          const periodTimes = times.filter((time) => toClock(time).period === period);
          if (!periodTimes.length) {
            return null;
          }
          return (
            <section key={period} className={styles.period}>
              <h4>{period}</h4>
              <div className={styles.grid}>
                {periodTimes.map((time) => {
                  const status = statusOf.get(time) ?? 'OFF';
                  const selected = time >= startTime && time < endTime;
                  const disabled = Boolean(minStart) && time < minStart;
                  const classes = [
                    styles.slot,
                    styles[status.toLowerCase()],
                    selected && styles.selected,
                  ];
                  return (
                    <button
                      key={time}
                      type="button"
                      className={classes.filter(Boolean).join(' ')}
                      aria-pressed={selected}
                      aria-label={`${period} ${toClock(time).label} ${disabled ? '선택 불가' : STATUS_LABELS[status]}`}
                      disabled={disabled}
                      onClick={() => handleClick(time)}
                    >
                      {toClock(time).label}
                    </button>
                  );
                })}
              </div>
            </section>
          );
        })}
      </div>
      {canExpand && (
        <button type="button" className={styles.more} onClick={() => setShowAll(!showAll)}>
          {showAll ? '영업시간 주변만 보기' : '전체 시간 보기 (0시~23시 30분)'}
        </button>
      )}
    </div>
  );
}
