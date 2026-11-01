import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import Panel from '@/components/common/Panel';
import { timePart } from '@/utils/scheduleDate';
import BookingCalendar from './BookingCalendar';
import styles from '../ShopDetailPage.module.css';

const TIME_UNIT_MINUTES = 30;
const DAY_LABELS = ['일', '월', '화', '수', '목', '금', '토'];

const toMinutes = (time) => {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 60 + minute;
};
const toTime = (minutes) =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

/**
 * 영업시간 안에서 시술이 끝날 수 있는 30분 단위 시작 시각에, 서버가 알려준 시작 가능 시각을 합친다.
 * 예약 가능 여부는 서버 응답(available-times)만 따른다. 영업시간 밖이라도 사장님이 연 시각은 서버 응답에 들어 있다.
 */
const buildSlots = (businessHour, durationMinutes, startTimes, extendedStartTimes = []) => {
  const available = new Set(startTimes.map(timePart));
  const extended = new Set(extendedStartTimes.map(timePart));
  const times = new Set(available);
  if (businessHour && !businessHour.closed) {
    const lastStart = toMinutes(businessHour.closeTime) - durationMinutes;
    for (let minutes = toMinutes(businessHour.openTime); minutes <= lastStart; minutes += TIME_UNIT_MINUTES) {
      times.add(toTime(minutes));
    }
  }
  return [...times]
    .sort()
    .map((time) => ({ time, available: available.has(time), extended: extended.has(time) }));
};

/**
 * 연장 영업 시각이 있는 날의 한 줄 안내. 휴무일에 따로 연 날과, 평소 영업시간 밖에도 연 날을 나눠 알린다.
 * 연 시간 끝에 다른 예약이 있으면 가능 시각만으로는 실제 연장 마감을 알 수 없어, 마감 시각은 적지 않는다.
 */
const extendedNotice = (businessHour, extendedStartTimes) => {
  if (!extendedStartTimes.length) {
    return null;
  }
  if (!businessHour || businessHour.closed) {
    return '휴무일이지만 사장님이 따로 연 시간이에요.';
  }
  return `평소 영업시간(${businessHour.openTime}~${businessHour.closeTime}) 밖에도 연 시간이 있어요. '연장' 표시 시간을 확인해 주세요.`;
};

// 화면 읽기 도구용 칸 이름. 진행 시간이면서 연장 영업인 칸은 두 상태를 함께 읽는다
const slotLabel = (time, { inProgress, extended }) => {
  const states = [];
  if (inProgress) {
    states.push('선택한 시술 진행 시간');
  }
  if (extended) {
    states.push('연장 영업');
  }
  return states.length ? `${time} ${states.join(', ')}` : undefined;
};

const dateLabel = (date) => {
  const [, month, day] = date.split('-').map(Number);
  return `${month}월 ${day}일 (${DAY_LABELS[new Date(`${date}T00:00:00Z`).getUTCDay()]})`;
};

/** 달력에서 날짜를 고르고, 그 날의 30분 단위 시작 시각 중 예약 가능한 시각을 고른다 */
export default function SlotPicker({
  firstDate,
  lastDate,
  isClosed,
  selectedDate,
  onSelectDate,
  businessHour,
  durationMinutes,
  times,
  onRetry,
  selectedTime,
  onSelectTime,
}) {
  const extendedStartTimes = times.extendedStartTimes ?? [];
  const slots =
    times.status === 'succeeded'
      ? buildSlots(businessHour, durationMinutes, times.startTimes, extendedStartTimes)
      : [];
  const notice =
    times.status === 'succeeded' ? extendedNotice(businessHour, extendedStartTimes) : null;
  const availableCount = slots.filter((slot) => slot.available).length;
  // 시술은 시작 시각부터 소요 시간 동안 이어지므로, 고른 시각 뒤 칸도 함께 잡힌 것으로 표시한다
  const selectionEnd = selectedTime ? toMinutes(selectedTime) + durationMinutes : null;
  const inSelection = (time) =>
    selectedTime != null && time > selectedTime && toMinutes(time) < selectionEnd;

  return (
    <Panel as="section" padded>
      <div className="b-row b-panel-heading">
        <h2>날짜·시간 선택</h2>
        <span className={styles.help}>오늘부터 60일 · 30분 단위 · 소요 시간 {durationMinutes}분</span>
      </div>
      <BookingCalendar
        firstDate={firstDate}
        lastDate={lastDate}
        selectedDate={selectedDate}
        isClosed={isClosed}
        onSelect={onSelectDate}
      />
      <h3 className={styles.slotDate}>{dateLabel(selectedDate)}</h3>
      {times.status === 'loading' && (
        <p className={styles.empty} role="status">
          예약 가능 시간을 불러오는 중입니다.
        </p>
      )}
      {times.status === 'failed' && (
        <div className={styles.timesError}>
          <Alert tone="error" role="alert">
            {times.error?.message}
          </Alert>
          <Button type="button" size="small" onClick={onRetry}>
            다시 시도
          </Button>
        </div>
      )}
      {times.status === 'succeeded' && slots.length === 0 && (
        <p className={styles.empty}>휴무일입니다. 다른 날짜를 선택해 주세요.</p>
      )}
      {times.status === 'succeeded' && slots.length > 0 && (
        <>
          {notice && <p className={styles.extendedNotice}>{notice}</p>}
          <div className={`b-time-grid ${styles.slots}`} role="group" aria-label="시작 시간">
            {slots.map(({ time, available, extended }) => (
              <button
                key={time}
                type="button"
                disabled={!available}
                className={
                  time === selectedTime ? 'is-selected' : inSelection(time) ? styles.inRange : undefined
                }
                aria-pressed={time === selectedTime}
                aria-label={slotLabel(time, {
                  inProgress: inSelection(time),
                  extended: extended && available,
                })}
                onClick={() => onSelectTime(time)}
              >
                {time}
                {extended && available && (
                  <span className={styles.extendedTag} aria-hidden="true">
                    연장
                  </span>
                )}
              </button>
            ))}
          </div>
          {availableCount === 0 && (
            <p className={styles.empty}>이 날은 예약 가능한 시간이 없습니다. 다른 날짜를 선택해 주세요.</p>
          )}
        </>
      )}
    </Panel>
  );
}
