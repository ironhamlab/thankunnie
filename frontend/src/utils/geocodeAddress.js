import { hasKakaoMapKey, loadKakaoMap } from './kakaoMapLoader';

export const canGeocode = hasKakaoMapKey;

/** 주소를 표준 주소·좌표·시군구 코드로 변환한다. */
export async function geocodeAddress(query) {
  if (!query.trim()) throw new Error('주소를 입력해 주세요.');
  const maps = await loadKakaoMap();
  const results = await new Promise((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new Error('주소 확인이 지연되고 있습니다. 다시 시도해 주세요.')),
      10000,
    );
    new maps.services.Geocoder().addressSearch(query.trim(), (data, status) => {
      clearTimeout(timer);
      if (status === maps.services.Status.OK && data?.length) resolve(data);
      else reject(new Error('주소를 찾지 못했습니다. 도로명 또는 지번 주소로 다시 입력해 주세요.'));
    });
  });
  const first = results[0];
  const latitude = Number(Number(first.y).toFixed(7));
  const longitude = Number(Number(first.x).toFixed(7));
  const regionCode = first.address?.b_code?.slice(0, 5) ?? '';
  if (
    !/^\d{5}$/.test(regionCode) ||
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    Math.abs(latitude) > 90 ||
    Math.abs(longitude) > 180
  ) {
    throw new Error(
      '주소의 위치 정보를 확인하지 못했습니다. 정확한 도로명 또는 지번 주소를 입력해 주세요.',
    );
  }
  return {
    address: first.road_address?.address_name ?? first.address_name,
    latitude,
    longitude,
    regionCode,
  };
}
