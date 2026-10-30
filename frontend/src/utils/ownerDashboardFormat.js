import { timePart } from '@/utils/scheduleDate';

/** 분 → "2시간 30분", "45분", "0분" */
export const formatMinutes = (minutes) => {
  const total = Math.max(0, Math.round(minutes ?? 0));
  const hours = Math.floor(total / 60);
  const rest = total % 60;
  if (!hours) return `${rest}분`;
  return rest ? `${hours}시간 ${rest}분` : `${hours}시간`;
};

/** 카드 큰 숫자용 금액 (단위 '원'은 따로 붙인다) */
export const formatAmount = (amount) => (amount ?? 0).toLocaleString('ko-KR');

/** 서버가 계산한 비율(%) 표시. 대상이 없어 null 이면 '-' */
export const formatRate = (rate) => (rate === null || rate === undefined ? '-' : `${rate}%`);

/** 빈 시간대 [startsAt, endsAt) → "15:00~16:30" */
export const formatTimeRange = ({ startsAt, endsAt }) =>
  `${timePart(startsAt)}~${timePart(endsAt)}`;

/** 그래프 축 금액: 1만 이상은 "30만", 그 밖은 "5,000" */
export const formatAxisMoney = (amount) =>
  amount >= 10000
    ? `${Number((amount / 10000).toFixed(1)).toLocaleString('ko-KR')}만`
    : amount.toLocaleString('ko-KR');

/** YYYY-MM-DD → "11/2" */
export const formatShortDate = (date) => {
  const [, month, day] = date.split('-').map(Number);
  return `${month}/${day}`;
};

/** YYYY-MM-DD 가 속한 달의 1일·말일 */
export const monthRange = (date) => {
  const [year, month] = date.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const prefix = date.slice(0, 8);
  return { from: `${prefix}01`, to: `${prefix}${String(lastDay).padStart(2, '0')}` };
};
