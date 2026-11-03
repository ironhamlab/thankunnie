// 지구 평균 반지름(km)
const EARTH_RADIUS_KM = 6371;
// 1km 안쪽은 m 로, 그 밖은 km 소수 1자리로 보여준다
const METER_UNIT_MAX_KM = 1;
// 바로 옆 매장도 "0m" 로 보이지 않게 m 단위는 10m 단위로 올린다
const METER_STEP = 10;

const toRadian = (degree) => (degree * Math.PI) / 180;

/**
 * 두 위치 사이 직선 거리(km). 하버사인 공식을 쓴다. 길 거리가 아니라 정렬·안내용 어림값이다.
 * 위경도는 숫자나 숫자 문자열(서버 BigDecimal 응답)을 받는다.
 *
 * @param from { lat, lng }
 * @param to   { lat, lng }
 */
export function distanceKm(from, to) {
  const lat1 = toRadian(Number(from.lat));
  const lat2 = toRadian(Number(to.lat));
  const dLat = lat2 - lat1;
  const dLng = toRadian(Number(to.lng) - Number(from.lng));
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** 거리(km)를 "350m", "1.2km" 처럼 보여준다. */
export function formatDistance(km) {
  if (km < METER_UNIT_MAX_KM) {
    const meters = Math.max(METER_STEP, Math.ceil((km * 1000) / METER_STEP) * METER_STEP);
    return meters >= 1000 ? '1.0km' : `${meters}m`;
  }
  return `${km.toFixed(1)}km`;
}
