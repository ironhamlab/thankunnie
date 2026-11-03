import { datePart, timePart, toKstDateTime } from '@/utils/scheduleDate';
import { toMinutes, toTime } from '@/utils/vacancyPreview';

const DAY_CODES = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const UNIT_MINUTES = 30;
const MINUTES_PER_DAY = 24 * 60;
// 고객 예약 클릭 마감: 시작 20분 전
const BOOKING_DEADLINE_MS = 20 * 60 * 1000;

export const DAY_LABELS = { SUN: '일', MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토' };

const dayCodeOf = (date) => {
  const [year, month, day] = date.split('-').map(Number);
  return DAY_CODES[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
};

/**
 * 일정을 그날 [00:00, 24:00) 로 잘라 분 구간 [시작, 종료) 로 돌려준다. 그날과 겹치지 않으면 null.
 * 전날 시작했거나 다음 날 끝나는(다음 날 0시 포함) 일정도 그날 몫만 남긴다.
 */
export const clipToDate = (item, date) => {
  const startDate = datePart(item.startsAt);
  const endDate = datePart(item.endsAt);
  if (startDate > date || endDate < date) {
    return null;
  }
  const start = startDate < date ? 0 : toMinutes(timePart(item.startsAt));
  const end = endDate > date ? MINUTES_PER_DAY : toMinutes(timePart(item.endsAt));
  return start < end ? [start, end] : null;
};

const exceptionRanges = (exceptions, date, type) =>
  exceptions
    .filter((item) => item.exceptionType === type)
    .map((item) => clipToDate(item, date))
    .filter(Boolean);

/** 그날 요일의 영업시간 { openTime, closeTime }. 휴무거나 영업시간이 없으면 null */
export const dayHoursOf = (businessHours, date) => {
  const hours = businessHours.find((item) => item.dayOfWeek === dayCodeOf(date));
  return hours && !hours.closed ? { openTime: hours.openTime, closeTime: hours.closeTime } : null;
};

/** 2026-10-30 → 10/30(금) */
export const formatDayLabel = (date) =>
  `${Number(date.slice(5, 7))}/${Number(date.slice(8, 10))}(${DAY_LABELS[dayCodeOf(date)]})`;

/**
 * 그날 고른 [시작, 종료) 와 겹치는 닫기. 열기보다 닫기가 우선이라 열어도 예약을 받지 않는다.
 * 종료는 하루 끝을 '24:00' 으로 넘길 수 있고, 전날부터 이어진 닫기도 그날 몫으로 잘라 비교한다.
 */
export const overlappingCloses = (exceptions, date, startTime, endTime, excludeId) => {
  const start = toMinutes(startTime);
  const end = toMinutes(endTime);
  return exceptions.filter((item) => {
    const range =
      item.exceptionType === 'CLOSE' && item.exceptionId !== excludeId && clipToDate(item, date);
    return Boolean(range) && range[0] < end && range[1] > start;
  });
};

/** 닫았거나 예약이 있거나 접수 마감이 지난 칸. 열어도 고객에게 팔 수 없어 미리보기에서 뺀다 */
export const unsellableTimes = (slots) =>
  new Set(
    slots
      .filter((slot) => ['CLOSED', 'BOOKED', 'PASSED'].includes(slot.status))
      .map((slot) => slot.time),
  );

const covers = (ranges, minutes) =>
  ranges.some(([start, end]) => minutes >= start && minutes < end);

/**
 * 그날 영업시간·열기 범위를 30분 칸으로 펼치고 칸마다 상태를 붙인다. 매장 화면 안내용이다.
 * 예약 가능 여부는 서버가 준 availableTimes 를 그대로 쓰고, 나머지는 안 되는 이유만 화면에서 구분한다.
 * 영업 중이고 닫지 않았고 마감 전인데 가능하지 않은 칸은 예약(또는 결제 대기)이 차지한 칸이다.
 *
 * @return {{ time: string, status: 'AVAILABLE'|'PASSED'|'CLOSED'|'BOOKED'|'OFF' }[]}
 */
export const buildDaySlots = ({
  date,
  businessHours,
  exceptions,
  availableTimes,
  now = Date.now(),
}) => {
  const hours = businessHours.find((item) => item.dayOfWeek === dayCodeOf(date));
  const openRanges = [
    ...(hours && !hours.closed ? [[toMinutes(hours.openTime), toMinutes(hours.closeTime)]] : []),
    ...exceptionRanges(exceptions, date, 'OPEN'),
  ];
  if (!openRanges.length) {
    return [];
  }
  const closeRanges = exceptionRanges(exceptions, date, 'CLOSE');
  const available = new Set(availableTimes.map(timePart));
  const from = Math.min(...openRanges.map(([start]) => start));
  const to = Math.max(...openRanges.map(([, end]) => end));

  const slots = [];
  for (let minutes = from; minutes < to; minutes += UNIT_MINUTES) {
    const time = toTime(minutes);
    const passed = Date.parse(toKstDateTime(date, time)) - BOOKING_DEADLINE_MS <= now;
    let status = 'BOOKED';
    if (available.has(time)) {
      status = 'AVAILABLE';
    } else if (!covers(openRanges, minutes)) {
      status = 'OFF';
    } else if (passed) {
      status = 'PASSED';
    } else if (covers(closeRanges, minutes)) {
      status = 'CLOSED';
    }
    slots.push({ time, status });
  }
  return slots;
};
