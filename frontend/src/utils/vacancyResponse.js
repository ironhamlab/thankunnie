import { todayKst } from './scheduleDate.js';

export const REGION_CODES = {
  '강남/서초': ['11680', '11650'],
  '홍대/연남': ['11440'],
  '성수/건대': ['11200', '11215'],
  '잠실/송파': ['11710'],
  '판교/분당': ['41135'],
};
const TIME_FORMAT = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});
const DATE_FORMAT = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' });

/** C 응답을 기존 카드의 표시 모델로 변환한다. 가격·할인율은 계산하지 않는다. */
export function toVacancyService(item, today = todayKst()) {
  if (
    !item ||
    typeof item.vacancyId !== 'string' ||
    !item.vacancyId ||
    !Number.isSafeInteger(item.treatmentId) ||
    item.treatmentId <= 0 ||
    typeof item.shopName !== 'string' ||
    !item.shopName.trim() ||
    typeof item.treatmentName !== 'string' ||
    !item.treatmentName.trim() ||
    !Number.isSafeInteger(item.salePrice) ||
    item.salePrice < 0 ||
    typeof item.startsAt !== 'string' ||
    !Number.isFinite(Date.parse(item.startsAt))
  ) {
    throw new Error('빈자리 응답 형식이 올바르지 않습니다.');
  }
  const start = new Date(item.startsAt);
  const date = DATE_FORMAT.format(start);
  return {
    ...item,
    id: item.vacancyId,
    isApi: true,
    name: item.treatmentName,
    shop: item.shopName,
    location: item.address,
    category: item.categoryName,
    duration: item.durationMinutes,
    region: Object.keys(REGION_CODES).find((name) => REGION_CODES[name].includes(item.regionCode)),
    day: Math.round(
      (Date.parse(`${date}T00:00:00+09:00`) - Date.parse(`${today}T00:00:00+09:00`)) / 86400000,
    ),
    dateLabel: date,
    time: TIME_FORMAT.format(start),
    price: item.salePrice,
    original: item.regularPrice,
    discount: item.discountRate,
    urgency: { LAST_MINUTE: '초임박 특가', IMMINENT: '임박 특가', NORMAL: '일반 빈자리' }[
      item.priceTier
    ],
  };
}

export function hasValidCoordinates(item) {
  const lat = item.isApi ? item.latitude : item.lat;
  const lng = item.isApi ? item.longitude : item.lng;
  return (
    Number.isFinite(lat) && Math.abs(lat) <= 90 && Number.isFinite(lng) && Math.abs(lng) <= 180
  );
}
