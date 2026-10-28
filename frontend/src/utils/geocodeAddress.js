import { hasKakaoMapKey, loadKakaoMap } from '@/utils/kakaoMapLoader';

// 서버 매장 좌표는 소수점 7자리까지 받는다 (DECIMAL(10,7))
const COORDINATE_DIGITS = 7;
// 법정동코드(10자리) 앞 5자리가 시군구 지역코드다. 구독 알림의 지역 매칭 기준과 같다
const REGION_CODE_LENGTH = 5;

const roundCoordinate = (value) => Number(Number(value).toFixed(COORDINATE_DIGITS));

/** 카카오맵 키가 있어 주소 확인을 쓸 수 있는지. 없으면 화면에서 좌표·지역코드를 직접 입력한다. */
export const canGeocode = hasKakaoMapKey;

/**
 * 주소 문자열을 카카오 주소 검색으로 확인해 표준 주소·좌표·시군구 지역코드를 돌려준다.
 * 결과가 여러 개면 첫 번째를 쓴다. 찾지 못하거나 SDK 를 불러오지 못하면 오류를 던진다.
 *
 * @returns {Promise<{ address: string, latitude: number, longitude: number, regionCode: string }>}
 */
export async function geocodeAddress(query) {
  const maps = await loadKakaoMap();
  const geocoder = new maps.services.Geocoder();

  const results = await new Promise((resolve, reject) => {
    geocoder.addressSearch(query, (data, status) => {
      if (status === maps.services.Status.OK && data.length > 0) {
        resolve(data);
        return;
      }
      reject(new Error('주소를 찾지 못했습니다. 도로명 또는 지번 주소로 다시 입력해 주세요.'));
    });
  });

  const [first] = results;
  const regionCode = first.address?.b_code?.slice(0, REGION_CODE_LENGTH) ?? '';
  return {
    // 도로명 주소가 있으면 도로명, 없으면 지번 주소를 저장한다
    address: first.road_address?.address_name ?? first.address_name,
    latitude: roundCoordinate(first.y),
    longitude: roundCoordinate(first.x),
    regionCode,
  };
}
