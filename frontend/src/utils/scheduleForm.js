import { addDays, datePart, timePart, todayKst } from '@/utils/scheduleDate';
import { earliestTodayStart, toMinutes, toTime } from '@/utils/vacancyPreview';

// 닫기는 지금 이후 칸부터, 열기는 시작 30분 전까지만 등록할 수 있다
const LEAD_MINUTES = { CLOSE: 0, OPEN: 30 };
// 처음 잡아 두는 길이: 닫기는 점심 1시간, 열기는 2시간
const DEFAULT_LENGTH_MINUTES = { CLOSE: 60, OPEN: 120 };
const LAST_END = '23:30';
// 오늘 더 고를 칸이 없을 때의 값. 모든 칸이 이보다 이르므로 전부 막힌다
export const NO_MORE_TODAY = '24:00';

/** 그 날짜에 고를 수 있는 가장 이른 시작 칸. 오늘이 아니면 제한이 없어 null */
export const minStartOf = (type, date) =>
  date === todayKst() ? (earliestTodayStart(LEAD_MINUTES[type]) ?? NO_MORE_TODAY) : null;

/** 새 등록 폼. 오늘 더 고를 칸이 없으면 내일로 잡고, 12시 이후의 가장 이른 칸에서 시작한다 */
export const newForm = (exceptionType, date = todayKst()) => {
  let target = date;
  let minStart = minStartOf(exceptionType, target);
  if (minStart && minStart > '23:00') {
    target = addDays(target, 1);
    minStart = null;
  }
  const startTime = minStart && minStart > '12:00' ? minStart : '12:00';
  const end = Math.min(
    toMinutes(startTime) + DEFAULT_LENGTH_MINUTES[exceptionType],
    toMinutes(LAST_END),
  );
  return { exceptionType, date: target, startTime, endTime: toTime(end), reason: '' };
};

/** 목록의 일정을 수정 폼으로 */
export const formOf = (exception) => ({
  exceptionType: exception.exceptionType,
  date: datePart(exception.startsAt),
  startTime: timePart(exception.startsAt),
  endTime: timePart(exception.endsAt),
  reason: exception.reason ?? '',
});

/** 90분 → "1시간 30분" */
export const durationLabel = (startTime, endTime) => {
  const minutes = toMinutes(endTime) - toMinutes(startTime);
  const hours = Math.floor(minutes / 60);
  return [hours && `${hours}시간`, minutes % 60 && '30분'].filter(Boolean).join(' ');
};
