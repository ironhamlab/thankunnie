import { generatePath } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { addDays, datePart, timePart, todayKst } from '@/utils/scheduleDate';
import defaultTreatmentImage from '@/assets/images/default-treatment.svg';
import defaultShopImage from '@/assets/images/default-shop.svg';

export const TIME_OF_DAY = {
  '오전 (12시 이전)': 'MORNING',
  '오후 (12~18시)': 'AFTERNOON',
  '저녁 (18시 이후)': 'EVENING',
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

/**
 * 매장 상세(A, /shops/:shopId) 주소. 홈 마감 임박·매장 카드, 검색 목록, 지도가 모두 이 함수로 매장 상세에 간다.
 * 시술·시작 시각까지 고른 곳(마감 임박 카드, 시간 버튼)은 그 날짜·시간이 골라진 채로 열리도록 값을 붙인다.
 * 매장 상세는 아직 주소의 선택 값을 읽지 않아 지금은 매장 상세 첫 화면이 열린다.
 *
 * 매장 상세는 일반 시술만 보여주므로, 초보 시술로 가는 곳은 model 을 붙여 초보 시술 모집 상세(/shops/:shopId/models)로 보낸다.
 *
 * @param shopId    매장 ID
 * @param selection { date, treatmentId, startsAt, model } 중 아는 값
 */
export function shopPagePath(shopId, { date, treatmentId, startsAt, model } = {}) {
  const path = generatePath(model ? ROUTES.MODEL_RECRUIT_DETAIL : ROUTES.SHOP_DETAIL, { shopId });
  const query = new URLSearchParams();
  if (date) query.set('date', date);
  if (treatmentId && startsAt) {
    query.set('treatmentId', treatmentId);
    query.set('startsAt', startsAt);
  }
  return query.size ? `${path}?${query}` : path;
}

/**
 * 주소에서 받은 날짜가 실제 달력 날짜(YYYY-MM-DD)이고 min~max 안이면 그대로, 아니면 null 을 돌려준다.
 * 문자열 비교만 하면 2026-10-99 같은 값이 범위 안으로 통과한다
 */
export function validDate(value, min, max) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? '')) return null;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  const isRealDate =
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
  return isRealDate && value >= min && value <= max ? value : null;
}

// API가 반환한 상대 이미지 경로는 로컬에서도 백엔드 origin으로 요청한다.
// '/' 경로와 http(s) 주소가 아니면 API 요청으로 바꾸지 않고 기본 그림을 쓴다.
function apiImageUrl(imageUrl, fallback) {
  if (!imageUrl || !(imageUrl.startsWith('/') || /^https?:\/\//i.test(imageUrl))) return fallback;
  try {
    const apiBase = new URL(
      import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
      window.location.origin,
    );
    return new URL(imageUrl, apiBase).href;
  } catch {
    return fallback;
  }
}

/** 이미지가 없으면 기본 시술 그림을 쓴다 */
export const vacancyImage = (imageUrl) => apiImageUrl(imageUrl, defaultTreatmentImage);

/** 이미지가 없으면 기본 매장 그림을 쓴다 */
export const shopImage = (imageUrl) => apiImageUrl(imageUrl, defaultShopImage);

/** '서울특별시 강남구 테헤란로 1' 에서 카드에 쓸 동네 '강남구 테헤란로' 만 꺼낸다 */
export const areaName = (address) => address.split(' ').slice(1, 3).join(' ');

/** 매장 요약(C-02)의 가장 빠른 시작을 '오늘 17:00' 처럼 표시한다 */
export const earliestLabel = (shop, today = todayKst()) =>
  `${dayLabel(datePart(shop.earliestStartsAt), today)} ${timePart(shop.earliestStartsAt)}`;

/** 빈자리를 시술별로 묶는다. 시작 시각 순서는 그대로 둔다 */
export function groupByTreatment(vacancies) {
  const groups = new Map();
  vacancies.forEach((vacancy) => {
    const group = groups.get(vacancy.treatmentId);
    if (group) group.slots.push(vacancy);
    else groups.set(vacancy.treatmentId, { ...vacancy, slots: [vacancy] });
  });
  return [...groups.values()];
}

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
    bookingClosesAt: item.bookingClosesAt,
    original: item.regularPrice,
    price: item.salePrice,
    discount: item.discountRate,
    priceTier: item.priceTier,
    urgency: URGENCY[item.priceTier] ?? URGENCY.NORMAL,
    image: vacancyImage(item.imageUrl),
    // 이 시술 · 시각이 골라진 매장 페이지. 초보 시술은 참여 조건·촬영 안내가 보이는 모집 상세로 간다
    shopPath: shopPagePath(item.shopId, {
      date: datePart(item.startsAt),
      treatmentId: item.treatmentId,
      startsAt: item.startsAt,
      model: item.treatmentType === 'MODEL',
    }),
  };
}
