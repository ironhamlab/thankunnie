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

/** YYYY-MM-DD → "26.11" (월 단위 그래프 라벨) */
export const formatShortMonth = (date) => `${date.slice(2, 4)}.${Number(date.slice(5, 7))}`;

/** YYYY-MM-DD 가 속한 달의 1일·말일 */
export const monthRange = (date) => {
  const [year, month] = date.split('-').map(Number);
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const prefix = date.slice(0, 8);
  return { from: `${prefix}01`, to: `${prefix}${String(lastDay).padStart(2, '0')}` };
};

/**
 * 비교 기간 라벨. 올해 안이면 "10/2~10/31", 해가 다르면 "25.11.7~26.11.6"
 *
 * @param {{ from: string, to: string }} period 비교한(직전) 기간
 * @param {string} today 오늘 YYYY-MM-DD
 */
export const formatCompareRange = ({ from, to }, today) => {
  const thisYear = today.slice(0, 4);
  const short = (date) => {
    const [year, month, day] = date.split('-');
    return year === thisYear
      ? `${Number(month)}/${Number(day)}`
      : `${year.slice(2)}.${Number(month)}.${Number(day)}`;
  };
  return `${short(from)}~${short(to)} 대비`;
};

/** 기간 안에 요일마다 며칠이 있는지 { MONDAY: 5, ... } (둘 다 포함, YYYY-MM-DD) */
export const countWeekdays = (from, to) => {
  const counts = Object.fromEntries(WEEKDAYS.map((day) => [day, 0]));
  const end = Date.parse(to);
  for (let time = Date.parse(from); time <= end; time += 86400000) {
    // Date.parse(YYYY-MM-DD) 는 UTC 자정이라 getUTCDay 로 요일을 센다. 0 = 일요일
    counts[WEEKDAYS[(new Date(time).getUTCDay() + 6) % 7]] += 1;
  }
  return counts;
};

/** 하루 평균 값. 소수 첫째 자리까지, .0 은 뗀다 (3.2, 3) */
export const perDay = (value, days) => (days ? Math.round((value / days) * 10) / 10 : 0);

/**
 * 시간대 점유율(%). 매장은 한 번에 한 예약만 받으므로 그 시간대 1시간 중 예약이 찬 비율의 평균이다.
 * 휴무일도 날짜 수에 들어가 실제 영업일 기준보다 조금 낮게 나온다.
 */
export const occupancyRate = (bookedMinutes, days) =>
  days ? Math.min(100, Math.round((bookedMinutes / (days * 60)) * 100)) : 0;

/** YYYY-MM-DD 두 날짜 사이 일수 (둘 다 포함) */
export const daysBetween = (from, to) => (Date.parse(to) - Date.parse(from)) / 86400000 + 1;

/** 요일 enum → 한 글자 */
export const WEEKDAY_LABEL = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
};
export const WEEKDAYS = Object.keys(WEEKDAY_LABEL);

/**
 * 직전 기간 대비 변화. 비율(%)은 %p, 그 밖은 증감률(%)로 보여 준다.
 * 비교할 값이 없으면 null. higherIsBetter 가 false 인 지표(취소율 등)는 늘면 나쁜 쪽이다.
 *
 * @returns {{ text: string, tone: 'good'|'bad'|'flat' } | null}
 */
export const describeChange = (
  current,
  previous,
  { isRate = false, higherIsBetter = true } = {},
) => {
  if (current === null || current === undefined || previous === null || previous === undefined) {
    return null;
  }
  // 비율 지표는 %p 차이, 나머지는 증감률. 직전 값이 0 이면 증감률을 낼 수 없다
  if (!isRate && previous === 0) return null;
  const diff = isRate ? current - previous : ((current - previous) / previous) * 100;
  const rounded = Math.round(diff * 10) / 10;
  if (rounded === 0) return { text: '변화 없음', tone: 'flat' };
  const arrow = rounded > 0 ? '▲' : '▼';
  const unit = isRate ? '%p' : '%';
  const good = rounded > 0 === higherIsBetter;
  return { text: `${arrow} ${Math.abs(rounded)}${unit}`, tone: good ? 'good' : 'bad' };
};
