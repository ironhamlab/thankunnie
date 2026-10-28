import { datePart, timePart } from '@/utils/scheduleDate';

/** 금액 (예: 30,000원). 값이 없으면 '-' */
export const formatMoney = (amount) =>
  amount === null || amount === undefined ? '-' : `${amount.toLocaleString('ko-KR')}원`;

/** 서버 시각(+09:00) → YYYY-MM-DD HH:mm. 값이 없으면 '-' */
export const formatDateTime = (dateTime) =>
  dateTime ? `${datePart(dateTime)} ${timePart(dateTime)}` : '-';

/** 예약 시간 (예: 2026-11-02 14:00~15:00) */
export const formatReservationTime = (startsAt, endsAt) =>
  `${datePart(startsAt)} ${timePart(startsAt)}~${timePart(endsAt)}`;
