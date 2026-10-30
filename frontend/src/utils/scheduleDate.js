// 서버 시각은 모두 한국 시간(+09:00) ISO 문자열이므로 날짜·시각을 문자열로 다룬다.
const KST_OFFSET = '+09:00';
const KST_DATE_FORMAT = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' });

/** 오늘 날짜 (YYYY-MM-DD, 한국 시간) */
export const todayKst = () => KST_DATE_FORMAT.format(new Date());

/** YYYY-MM-DD 에 일수를 더한다 */
export const addDays = (date, days) => {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
};

/** 날짜와 HH:mm 을 서버 요청용 시각으로 만든다 */
export const toKstDateTime = (date, time) => `${date}T${time}:00${KST_OFFSET}`;

/** 서버 시각에서 날짜(YYYY-MM-DD)만 꺼낸다 */
export const datePart = (dateTime) => dateTime.slice(0, 10);

/** 서버 시각에서 HH:mm 만 꺼낸다 */
export const timePart = (dateTime) => dateTime.slice(11, 16);

/** 한국 시간 기준 지금 이후의 다음 30분 칸. 오늘이 끝나면 내일 00:00 */
export const nextHalfHourKst = () => {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(new Date());
  const hour = Number(parts.find((part) => part.type === 'hour')?.value ?? 0) % 24;
  const minute = Number(parts.find((part) => part.type === 'minute')?.value ?? 0);
  const minutes = hour * 60 + minute;
  const rounded = Math.ceil(minutes / 30) * 30;
  const next = rounded === minutes ? minutes + 30 : rounded;
  const date = next >= 24 * 60 ? addDays(todayKst(), 1) : todayKst();
  const slot = next >= 24 * 60 ? 0 : next;
  const time = `${String(Math.floor(slot / 60)).padStart(2, '0')}:${String(slot % 60).padStart(2, '0')}`;
  return { date, time };
};

/** 희망 시간 폼의 기본값. 시작은 다음 30분 칸, 종료는 그 이틀 뒤 20:00 */
export const defaultHopePeriod = () => {
  const start = nextHalfHourKst();
  return {
    startDate: start.date,
    startTime: start.time,
    endDate: addDays(start.date, 2),
    endTime: '20:00',
  };
};
