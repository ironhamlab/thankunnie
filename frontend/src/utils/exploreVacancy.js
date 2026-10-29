import { generatePath } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { addDays, datePart, timePart, todayKst } from '@/utils/scheduleDate';
import defaultTreatmentImage from '@/assets/images/default-treatment.svg';

// 화면의 지역 그룹 → 매장 지역 코드. docs/api/C-01-vacancy-search.md 9장 기준
export const REGION_CODES = {
  '강남/서초': ['11680', '11650'],
  '홍대/연남': ['11440'],
  '성수/건대': ['11200', '11215'],
  '잠실/송파': ['11710'],
  '판교/분당': ['41135'],
};

export const TIME_OF_DAY = {
  '오전 (12시 이전)': 'MORNING',
  '오후 (12~18시)': 'AFTERNOON',
  '저녁 (18시 이후)': 'EVENING',
};

export const SORTS = {
  '빠른 시작순': 'START_ASC',
  할인율순: 'DISCOUNT_DESC',
  '낮은 가격순': 'PRICE_ASC',
};

const URGENCY = {
  LAST_MINUTE: '초임박 특가',
  IMMINENT: '임박 특가',
  NORMAL: '일반 빈자리',
};

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const RELATIVE_DAYS = ['오늘', '내일', '모레'];

/** YYYY-MM-DD 를 '11.06 (금)' 으로 표시한다 */
function shortDate(date) {
  const [year, month, day] = date.split('-').map(Number);
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return `${String(month).padStart(2, '0')}.${String(day).padStart(2, '0')} (${weekday})`;
}

// 오늘부터 며칠째인지. 사흘 안이 아니면 -1
const relativeDay = (date, today) =>
  RELATIVE_DAYS.findIndex((_, index) => addDays(today, index) === date);

/** YYYY-MM-DD 를 '오늘'·'내일'·'모레' 또는 '11.06 (금)' 으로 표시한다 */
export function dayLabel(date, today = todayKst()) {
  const offset = relativeDay(date, today);
  return offset >= 0 ? RELATIVE_DAYS[offset] : shortDate(date);
}

/** 오늘부터 days 일의 날짜 선택지. 사흘 안은 '오늘 10.29 (목)' 처럼 함께 표시한다 */
export function upcomingDays(days, today = todayKst()) {
  return Array.from({ length: days }, (_, index) => {
    const date = addDays(today, index);
    const offset = relativeDay(date, today);
    return {
      date,
      label: offset >= 0 ? `${RELATIVE_DAYS[offset]} ${shortDate(date)}` : shortDate(date),
    };
  });
}

/** 빈자리 상세 화면 주소. 같은 시술도 시작 시각마다 가격·예약 가능 여부가 달라 시작 시각을 함께 넘긴다 */
export function vacancyDetailPath(treatmentId, startsAt) {
  const query = new URLSearchParams({ startsAt });
  return `${generatePath(ROUTES.SERVICE_DETAIL, { id: treatmentId })}?${query}`;
}

/** 이미지가 없으면 기본 시술 그림을 쓴다 */
export const vacancyImage = (imageUrl) => imageUrl || defaultTreatmentImage;

/**
 * 탐색 API(C-01) 빈자리 한 건을 카드·지도 표시용으로 바꾼다. 가격·할인율은 서버 값을 그대로 쓴다.
 */
export function toVacancyCard(item, today = todayKst()) {
  return {
    id: item.vacancyId,
    treatmentId: item.treatmentId,
    shopId: item.shopId,
    name: item.treatmentName,
    shop: item.shopName,
    location: item.address,
    lat: item.latitude,
    lng: item.longitude,
    category: item.categoryName,
    duration: item.durationMinutes,
    startsAt: item.startsAt,
    day: dayLabel(datePart(item.startsAt), today),
    time: timePart(item.startsAt),
    closesAt: timePart(item.bookingClosesAt),
    original: item.regularPrice,
    price: item.salePrice,
    discount: item.discountRate,
    urgency: URGENCY[item.priceTier] ?? URGENCY.NORMAL,
    image: vacancyImage(item.imageUrl),
    detailPath: vacancyDetailPath(item.treatmentId, item.startsAt),
  };
}
