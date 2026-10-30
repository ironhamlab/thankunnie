import { useState } from 'react';
import { TIME_OPTIONS, toMinutes, toTime } from '@/utils/vacancyPreview';
import styles from './TimeRangePicker.module.css';

// 영업시간이 늦게 시작해도 오전부터 고를 수 있게 9시부터 보여준다
const DEFAULT_FROM = '09:00';
// 열기·닫기는 하루 안에서 끝나야 해서 23:00 칸(~23:30)까지만 고른다
const LAST_START = '23:00';
const STATUS_LABELS = {
  AVAILABLE: '',
  BOOKED: '예약됨',
  CLOSED: '닫음',
  PASSED: '마감',
  OFF: '영업 외',
};

/**
 * 30분 칸을 눌러 시간대를 고른다. 시작 칸을 누른 뒤 끝 칸을 누르면 그 사이가 선택된다.
 * 칸마다 지금 상태(예약됨·닫음·영업 외)를 보여줘 어디를 열고 닫는지 한눈에 보이게 한다.
 *
 * @param slots     useDaySlots 의 칸 상태 (영업 범위 밖 칸은 영업 외로 본다)
 * @param minStart  이보다 이른 칸은 고를 수 없다 (오늘의 등록 마감). 없으면 제한 없음
 */
export default function TimeRangePicker({ slots, startTime, endTime, minStart, onChange }) {
  const [picking, setPicking] = useState(false);
  const statusOf = new Map(slots.map((slot) => [slot.time, slot.status]));
  const from = slots[0] && slots[0].time < DEFAULT_FROM ? slots[0].time : DEFAULT_FROM;
  const times = TIME_OPTIONS.filter((time) => time >= from && time <= LAST_START);

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
          ? `${startTime}부터 · 끝나는 칸을 눌러 주세요 (지금은 30분)`
          : '시작 칸을 누르고 끝나는 칸을 누르세요'}
      </p>
      <div className={styles.grid} role="group" aria-label="30분 칸 시간 선택">
        {times.map((time) => {
          const status = statusOf.get(time) ?? 'OFF';
          const selected = time >= startTime && time < endTime;
          const disabled = Boolean(minStart) && time < minStart;
          const classes = [
            styles.slot,
            styles[status.toLowerCase()],
            selected && styles.selected,
            selected && time === startTime && styles.first,
            selected && toTime(toMinutes(time) + 30) === endTime && styles.last,
          ];
          return (
            <button
              key={time}
              type="button"
              className={classes.filter(Boolean).join(' ')}
              aria-pressed={selected}
              disabled={disabled}
              title={`${time} ${disabled ? '선택 불가' : STATUS_LABELS[status] || '비어 있음'}`}
              onClick={() => handleClick(time)}
            >
              {time}
              <small>{disabled ? '마감' : STATUS_LABELS[status]}</small>
            </button>
          );
        })}
      </div>
    </div>
  );
}
