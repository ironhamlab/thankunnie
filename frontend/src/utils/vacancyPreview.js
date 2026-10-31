import { VACANCY_POLICY as P } from '@/constants/vacancyPolicy';
import { toKstDateTime } from '@/utils/scheduleDate';

const MINUTES_PER_DAY = 24 * 60;
const HOUR_MS = 60 * 60 * 1000;
const KST_TIME_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export const toMinutes = (time) => {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + minutes;
};

export const toTime = (minutes) =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

// 13:30 → { period: '오후', label: '1:30' }
export const toClock = (time) => {
  const [hours, minutes] = time.split(':').map(Number);
  return {
    period: hours < 12 ? '오전' : '오후',
    label: `${hours % 12 || 12}:${String(minutes).padStart(2, '0')}`,
  };
};

// 하루의 30분 단위 시각. 열기는 하루 안에서 끝나야 해서 23:30 까지만 고른다
export const TIME_OPTIONS = Array.from(
  { length: MINUTES_PER_DAY / P.TIME_UNIT_MINUTES },
  (_, index) => toTime(index * P.TIME_UNIT_MINUTES),
);

/**
 * 오늘 고를 수 있는 가장 이른 시작 시각 (지금 + leadMinutes 를 30분 단위로 올림). 오늘 더 고를 수 없으면 null.
 * 열기는 시작 30분 전까지만 등록할 수 있어 기본값이 30분이다.
 */
export const earliestTodayStart = (leadMinutes = P.OPEN_DEADLINE_MINUTES, now = new Date()) => {
  const unit = P.TIME_UNIT_MINUTES;
  const minutes = toMinutes(KST_TIME_FORMAT.format(now)) + leadMinutes;
  const rounded = Math.ceil(minutes / unit) * unit;
  return rounded < MINUTES_PER_DAY ? toTime(rounded) : null;
};

/**
 * 연 시간대 안에서 시술을 시작해 끝낼 수 있는 30분 단위 시작 시각. 시술 시간 중 막힌 칸(blocked)이 있으면 뺀다.
 * 영업시간과 이어지면 실제로는 더 늘 수 있다.
 */
export const startTimesWithin = (startTime, endTime, durationMinutes, blocked = new Set()) => {
  const unit = P.TIME_UNIT_MINUTES;
  const times = [];
  const end = toMinutes(endTime);
  for (let start = toMinutes(startTime); start + durationMinutes <= end; start += unit) {
    let free = true;
    for (let minutes = start; minutes < start + durationMinutes; minutes += unit) {
      free = free && !blocked.has(toTime(minutes));
    }
    if (free) {
      times.push(toTime(start));
    }
  }
  return times;
};

/**
 * 지금 기준으로 그 시작 시각에 고객이 보게 될 가격 구간. 실제 가격은 고객이 예약을 누른 시점에 서버가 정한다.
 */
export const priceTierAt = (date, time, now = Date.now()) => {
  const remainingHours = (Date.parse(toKstDateTime(date, time)) - now) / HOUR_MS;
  if (remainingHours <= P.LAST_MINUTE_HOURS) {
    return 'LAST_MINUTE';
  }
  return remainingHours <= P.IMMINENT_HOURS ? 'IMMINENT' : 'NORMAL';
};

export const discountRate = (regularPrice, salePrice) =>
  regularPrice > 0 ? Math.floor(((regularPrice - salePrice) * 100) / regularPrice) : 0;
