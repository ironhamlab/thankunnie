import { useState } from 'react';
import EmptyState from '@/components/common/EmptyState';
import { LAST_END, LAST_START } from '@/utils/scheduleForm';
import { TIME_OPTIONS, toMinutes, toTime } from '@/utils/vacancyPreview';
import styles from './TimeRangePicker.module.css';

// 영업하지 않는 날 접힌 상태에서 보여줄 첫 칸
const DEFAULT_FROM = '09:00';
const FIRST_START = '00:00';
// HH:mm 를 분만큼 옮긴다 (하루 범위 안)
const shift = (time, minutes) =>
  toTime(Math.min(Math.max(toMinutes(time) + minutes, 0), 23 * 60 + 30));
const STATUS_LABELS = {
  AVAILABLE: '예약 가능',
  BOOKED: '예약됨',
  CLOSED: '닫음',
  PASSED: '마감',
  OFF: '영업 외',
};
// 칸에는 글자를 넣지 않고 색으로만 구분하므로, 화면에 나온 상태만 범례로 알려준다.
// 한 단 화면에서는 날짜별 예약 가능 시간 패널이 숨겨져 이 범례로 모든 상태를 구분한다
const LEGEND_STATUSES = ['AVAILABLE', 'BOOKED', 'CLOSED', 'PASSED', 'OFF'];
const periodOf = (time) => (time < '12:00' ? '오전' : '오후');

/**
 * 30분 칸을 눌러 시간대를 고른다. 시작 칸을 누른 뒤 끝 칸을 누르면 그 사이가 선택된다.
 * 오전·오후로 나눠 보여주고, 칸의 상태(예약됨·닫음·영업 외)는 색과 범례로 구분한다.
 * 열기는 영업 범위 앞뒤 1시간을 기본으로 보여주고, 영업시간 밖을 열 수 있게 전체 시간으로 펼칠 수 있다.
 * 닫기는 영업 중이거나 직접 연 칸만 보여준다. 고른 구간이 영업 외면 입력란 아래 경고가 다시 고르라고 알려준다.
 *
 * @param closing   닫기면 true
 * @param slots     useDaySlots 의 칸 상태 (영업 범위 밖 칸은 영업 외로 본다)
 * @param minStart  이보다 이른 칸은 고를 수 없다 (오늘의 등록 마감). 없으면 제한 없음
 */
export default function TimeRangePicker({
  closing,
  slots,
  startTime,
  endTime,
  minStart,
  onChange,
}) {
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
  const isOff = (time) => (statusOf.get(time) ?? 'OFF') === 'OFF';
  const isSelected = (time) => time >= startTime && time < endTime;
  const times = TIME_OPTIONS.filter((time) =>
    closing ? !isOff(time) : time >= from && time <= to,
  );
  const canExpand = !closing && (showAll || from > FIRST_START || to < LAST_START);
  // 오늘은 지금부터 30분 뒤 칸부터 열 수 있고, 늦어서 고를 칸이 없으면 입력란 아래 경고가 알려준다
  const openFrom = minStart ?? FIRST_START;
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

  if (closing && !slots.length) {
    return <EmptyState as="p">이 날은 영업하지 않아 닫을 시간이 없어요.</EmptyState>;
  }

  return (
    <div className={styles.root}>
      <p className={styles.hint} aria-live="polite">
        {picking
          ? `${startTime}부터 · 끝나는 칸을 눌러 주세요`
          : '시작 칸을 누르고 끝나는 칸을 누르세요'}
      </p>
      {!closing && openFrom <= LAST_START && (
        <p className={styles.rule}>
          {minStart ? '오늘은 ' : ''}
          {openFrom}부터 {LAST_END}까지 열 수 있어요. 시술이 끝날 시간까지 넉넉히 열어 주세요.
        </p>
      )}
      {canExpand && (
        <button type="button" className={styles.more} onClick={() => setShowAll(!showAll)}>
          {showAll ? '영업시간 주변만 보기' : '더 일찍·늦게 열려면 전체 시간 보기'}
        </button>
      )}
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
          const periodTimes = times.filter((time) => periodOf(time) === period);
          if (!periodTimes.length) {
            return null;
          }
          return (
            <section key={period} className={styles.period}>
              <h4>{period}</h4>
              <div className={styles.grid}>
                {periodTimes.map((time) => {
                  const status = statusOf.get(time) ?? 'OFF';
                  const selected = isSelected(time);
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
                      aria-label={`${time} ${disabled ? '선택 불가' : STATUS_LABELS[status]}`}
                      disabled={disabled}
                      onClick={() => handleClick(time)}
                    >
                      {time}
                    </button>
                  );
                })}
              </div>
            </section>
          );
        })}
      </div>
    </div>
  );
}
