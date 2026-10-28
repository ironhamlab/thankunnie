const OFFSET_DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?(?:Z|[+-](\d{2}):(\d{2}))$/;

function isValidDateString(value) {
  if (typeof value !== 'string') return false;
  const parts = OFFSET_DATE_TIME_PATTERN.exec(value);
  if (!parts) return false;
  const [year, month, day, hour, minute, second] = parts
    .slice(1, 7)
    .map((part) => Number(part ?? 0));
  const offsetHour = Number(parts[7] ?? 0);
  const offsetMinute = Number(parts[8] ?? 0);
  if (
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > 31 ||
    hour > 23 ||
    minute > 59 ||
    second > 59 ||
    offsetHour > 23 ||
    offsetMinute > 59
  )
    return false;
  // Date.UTC의 0~99년 보정을 피하고 달력 날짜 자체도 검증한다.
  const calendarDate = new Date(0);
  calendarDate.setUTCFullYear(year, month - 1, day);
  return (
    calendarDate.getUTCFullYear() === year &&
    calendarDate.getUTCMonth() === month - 1 &&
    calendarDate.getUTCDate() === day &&
    Number.isFinite(Date.parse(value))
  );
}

function isCoordinate(value, limit) {
  return typeof value === 'number' && Number.isFinite(value) && value >= -limit && value <= limit;
}

/** 좌표 미등록은 두 필드를 모두 null로 반환하고 목록에는 유지한다. */
export function hasItemCoordinates(item) {
  return isCoordinate(item.latitude, 90) && isCoordinate(item.longitude, 180);
}

/**
 * @typedef {Object} MainItem
 * @property {number} id
 * @property {string} title
 * @property {string} startsAt 오프셋을 포함한 ISO-8601 시각
 * @property {string|null} applicationClosesAt
 * @property {string} shopName
 * @property {number} price 서버가 계산한 원화 판매가
 * @property {number|null} latitude
 * @property {number|null} longitude
 */

/** @returns {boolean} */
export function isMainItem(item) {
  return (
    item !== null &&
    typeof item === 'object' &&
    Number.isSafeInteger(item.id) &&
    item.id > 0 &&
    typeof item.title === 'string' &&
    item.title.trim().length > 0 &&
    isValidDateString(item.startsAt) &&
    (item.applicationClosesAt === null || isValidDateString(item.applicationClosesAt)) &&
    typeof item.shopName === 'string' &&
    item.shopName.trim().length > 0 &&
    Number.isSafeInteger(item.price) &&
    item.price >= 0 &&
    ((item.latitude === null && item.longitude === null) || hasItemCoordinates(item))
  );
}
