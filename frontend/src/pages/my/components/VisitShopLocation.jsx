import { useMemo } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useShopDetailStore } from '@/store/useShopDetailStore';
import KakaoMap from '@/components/map/KakaoMap';
import styles from './VisitShopLocation.module.css';

const KAKAO_MAP_LINK = 'https://map.kakao.com/link';
// 동네가 보이는 넓은 지도에서 시작해 골목이 보이는 단계까지 확대한다
const START_LEVEL = 6;
const SHOP_LEVEL = 3;

/**
 * 예약 상세 창의 방문 매장 안내. 지도와 주소·전화·길찾기, 매장 상세로 가는 링크를 보여준다.
 * 승인이 취소되는 등 매장 정보를 받지 못하면 아무것도 그리지 않고, 예약 상세는 그대로 볼 수 있게 한다.
 */
export default function VisitShopLocation() {
  const shop = useShopDetailStore((state) => state.shop);
  const status = useShopDetailStore((state) => state.status);
  const markers = useMemo(
    () =>
      shop
        ? [
            {
              id: shop.shopId,
              lat: Number(shop.latitude),
              lng: Number(shop.longitude),
              label: shop.shopName,
              ariaLabel: `${shop.shopName} 위치`,
            },
          ]
        : [],
    [shop],
  );

  if (status === 'failed') {
    return null;
  }
  if (!shop) {
    // 받아 오는 동안 지도 자리를 잡아 창 내용이 밀리지 않게 한다
    return <div className={styles.map} role="status" aria-label="매장 위치를 불러오는 중입니다." />;
  }

  // 모바일 카카오맵은 길찾기 도착지를 이름으로 다시 검색하므로, 항상 찾을 수 있는 도로명 주소를 도착지 이름으로 쓴다
  const destination = `${encodeURIComponent(shop.address)},${shop.latitude},${shop.longitude}`;

  return (
    <section className={styles.root} aria-label="방문 매장">
      <div className={styles.map}>
        <KakaoMap
          markers={markers}
          markerClassName={styles.marker}
          level={SHOP_LEVEL}
          startLevel={START_LEVEL}
        />
      </div>
      <p className={styles.address}>
        {[shop.address, shop.addressDetail].filter(Boolean).join(' ')}
      </p>
      <div className={styles.links}>
        {shop.phone && <a href={`tel:${shop.phone}`}>{shop.phone}</a>}
        <a href={`${KAKAO_MAP_LINK}/to/${destination}`} target="_blank" rel="noopener noreferrer">
          길찾기 ↗
        </a>
        {/* 다시 예약하거나 시술·후기를 볼 수 있게 매장 상세로 보낸다 */}
        <Link to={generatePath(ROUTES.SHOP_DETAIL, { shopId: String(shop.shopId) })}>
          매장 보러 가기 ›
        </Link>
      </div>
    </section>
  );
}
