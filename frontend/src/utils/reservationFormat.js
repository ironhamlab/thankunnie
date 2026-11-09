import { datePart, timePart, todayKst } from '@/utils/scheduleDate';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const DAY_MS = 24 * 60 * 60 * 1000;

/** 금액 (예: 30,000원). 값이 없으면 '-' */
export const formatMoney = (amount) =>
  amount === null || amount === undefined ? '-' : `${amount.toLocaleString('ko-KR')}원`;

/** 서버 시각(+09:00) → YYYY-MM-DD HH:mm. 값이 없으면 '-' */
export const formatDateTime = (dateTime) =>
  dateTime ? `${datePart(dateTime)} ${timePart(dateTime)}` : '-';

/** 예약 시간 (예: 2026-11-02 14:00~15:00) */
export const formatReservationTime = (startsAt, endsAt) =>
  `${datePart(startsAt)} ${timePart(startsAt)}~${timePart(endsAt)}`;

/** 방문 날짜 (예: 11월 2일 (월)) */
export const formatVisitDate = (dateTime) => {
  const [year, month, day] = datePart(dateTime).split('-').map(Number);
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return `${month}월 ${day}일 (${weekday})`;
};

/** 방문일까지 남은 날. 사흘 안이면 오늘·내일·모레, 그 뒤는 D-3 처럼 보여준다 */
export const visitDayLabel = (dateTime) => {
  const days = Math.round((Date.parse(datePart(dateTime)) - Date.parse(todayKst())) / DAY_MS);
  return ['오늘', '내일', '모레'][days] ?? `D-${days}`;
};
