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
