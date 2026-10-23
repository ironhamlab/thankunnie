const wonFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});
const STATUS_LABELS = Object.freeze({
  AVAILABLE: '예약 가능',
  HELD: '임시 확보 중',
  BOOKED: '예약 완료',
  SOLD_OUT: '매진',
  CLOSED: '마감',
  CANCELLED: '취소',
});

export function formatItemPrice(value) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? wonFormatter.format(value)
    : '가격 확인 필요';
}

export function getItemStatusLabel(status) {
  return Object.hasOwn(STATUS_LABELS, status) ? STATUS_LABELS[status] : '상태 확인 필요';
}

export function formatItemDate(value) {
  if (typeof value !== 'string' || !value.trim()) return '일정 확인 필요';
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? dateFormatter.format(date) : '일정 확인 필요';
}

export function hasDiscount(item) {
  return (
    typeof item.price === 'number' &&
    Number.isFinite(item.price) &&
    item.price >= 0 &&
    typeof item.originalPrice === 'number' &&
    Number.isFinite(item.originalPrice) &&
    item.originalPrice > item.price
  );
}

/** API가 제공한 이미지 URL만 사용하고 누락 이미지는 카드의 빈 이미지 상태로 표시한다. */
export function getItemImageUrl(value) {
  if (typeof value !== 'string' || !value.trim()) return null;
  if (value.startsWith('/') && !value.startsWith('//') && !value.includes('\\')) return value;
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}
