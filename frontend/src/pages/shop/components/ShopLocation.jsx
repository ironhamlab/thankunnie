import { useMemo } from 'react';
import KakaoMap from '@/components/map/KakaoMap';
import styles from './ShopLocation.module.css';

const KAKAO_MAP_LINK = 'https://map.kakao.com/link';
// 매장 하나만 보여주므로 골목이 보일 만큼 가깝게 본다
const SHOP_LEVEL = 3;

/**
 * 매장 상세정보의 위치 지도 (C, 문서 C-01 7장). 매장 이름 마커 1개와 카카오맵 길찾기·크게 보기 링크를 보여준다.
 * 카카오맵 키가 없거나 SDK를 불러오지 못하면 지도는 빼고 링크만 남긴다(주소는 위 상세정보에 있다).
 */
export default function ShopLocation({ shop }) {
  const { shopId, shopName, address, latitude, longitude } = shop;
  const markers = useMemo(
    () => [
      {
        id: shopId,
        lat: Number(latitude),
        lng: Number(longitude),
        label: shopName,
        ariaLabel: `${shopName} 위치`,
      },
    ],
    [shopId, shopName, latitude, longitude],
  );
  // 카카오맵 링크 형식: /link/to/이름,위도,경도 (길찾기 도착지), /link/map/이름,위도,경도 (지도에서 보기)
  const place = `${encodeURIComponent(shopName)},${latitude},${longitude}`;
  // 모바일 카카오맵은 길찾기 도착지를 좌표가 아니라 이름으로 다시 검색한다. 카카오에 등록되지 않은 매장 이름은
  // "검색 결과 없음"이 되므로, 항상 찾을 수 있는 도로명 주소를 도착지 이름으로 쓴다
  const destination = `${encodeURIComponent(address)},${latitude},${longitude}`;

  return (
    <section className={styles.root} aria-label="매장 위치">
      <h3 className={styles.heading}>위치</h3>
      <div className={styles.map}>
        <KakaoMap markers={markers} markerClassName={styles.marker} level={SHOP_LEVEL} />
      </div>
      <div className={styles.links}>
        <a href={`${KAKAO_MAP_LINK}/to/${destination}`} target="_blank" rel="noopener noreferrer">
          카카오맵 길찾기 ↗
        </a>
        <a href={`${KAKAO_MAP_LINK}/map/${place}`} target="_blank" rel="noopener noreferrer">
          지도 크게 보기 ↗
        </a>
      </div>
    </section>
  );
}
