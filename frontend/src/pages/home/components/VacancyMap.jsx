import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import MapArtwork from '@/pages/home/components/MapArtwork';
import KakaoMap from '@/components/map/KakaoMap';
import { money } from '@/utils/demoData';
import Button from '@/components/common/Button';
import styles from './VacancyMap.module.css';

const PIN_POSITIONS = [
  [30, 30],
  [65, 24],
  [42, 52],
  [73, 60],
  [23, 70],
  [61, 80],
];
// 아래쪽 상세 패널에 핀이 가리지 않도록 범위를 맞출 때 아래 여백을 넓게 둔다
const MAP_BOUNDS_PADDING = { top: 60, right: 70, bottom: 200, left: 50 };

/**
 * 검색 결과 지도. 같은 매장의 빈자리는 한 위치라 매장마다 핀 하나(최저 판매가)를 찍고,
 * 핀을 고르면 그 매장에서 가장 앞에 있는 빈자리를 보여준다.
 */
export default function VacancyMap({ services }) {
  const [selectedShopId, setSelectedShopId] = useState(null);
  const shops = useMemo(() => {
    const byShop = new Map();
    services.forEach((service) => {
      const shop = byShop.get(service.shopId);
      if (!shop) {
        byShop.set(service.shopId, { ...service, minPrice: service.price });
      } else if (service.price < shop.minPrice) {
        shop.minPrice = service.price;
      }
    });
    return [...byShop.values()];
  }, [services]);
  const selected = shops.find((shop) => shop.shopId === selectedShopId) || shops[0];
  // 좌표가 있는 매장만 카카오맵에 표시한다
  const markers = useMemo(
    () =>
      shops
        .filter((shop) => shop.lat != null && shop.lng != null)
        .map((shop) => ({
          id: shop.shopId,
          lat: shop.lat,
          lng: shop.lng,
          label: money(shop.minPrice),
          ariaLabel: `${shop.shop} ${money(shop.minPrice)}부터`,
        })),
    [shops],
  );
  return (
    <section className={`b-map ${styles.root}`} aria-label="빈자리 지도">
      <KakaoMap
        markers={markers}
        selectedId={selected.shopId}
        onSelect={setSelectedShopId}
        markerClassName={`${styles.pin} ${styles.kakaoPin}`}
        boundsPadding={MAP_BOUNDS_PADDING}
        fallback={
          <ExampleMap shops={shops} selectedId={selected.shopId} onSelect={setSelectedShopId} />
        }
      />
      <div className={`${styles.detail} b-panel`}>
        <div className="b-flex">
          <img src={selected.image} className={styles.thumb} alt={selected.shop} />
          <div>
            <h2>{selected.shop}</h2>
            <p>
              {selected.name} · {selected.day} {selected.time}
            </p>
            <strong>{money(selected.price)}</strong>
          </div>
        </div>
        <Button as={Link} variant="primary" to={selected.detailPath}>
          선택한 빈자리 확인
        </Button>
      </div>
    </section>
  );
}

// 카카오맵 키가 없거나 SDK를 불러오지 못할 때 보여주는 예시 지도. 핀 위치는 실제 위치가 아니다
function ExampleMap({ shops, selectedId, onSelect }) {
  const [zoom, setZoom] = useState(1);
  return (
    <>
      <div className={styles.canvas} style={{ transform: `scale(${zoom})` }}>
        <MapArtwork />
        {shops.map((shop, index) => {
          const [left, top] = PIN_POSITIONS[index % PIN_POSITIONS.length];
          return (
            <button
              key={shop.shopId}
              className={`${styles.pin} ${selectedId === shop.shopId ? 'is-selected' : ''}`}
              style={{
                left: `${left}%`,
                top: `${top}%`,
              }}
              aria-label={`${shop.shop} ${money(shop.minPrice)}부터`}
              aria-pressed={selectedId === shop.shopId}
              onClick={() => onSelect(shop.shopId)}
            >
              {money(shop.minPrice)}
            </button>
          );
        })}
      </div>
      <span className={styles.note}>예시 지도 · 실제 위치와 다를 수 있어요</span>
      <div className={styles.tools}>
        <button
          aria-label="지도 확대"
          disabled={zoom >= 1.4}
          onClick={() => setZoom((value) => Math.min(1.4, value + 0.2))}
        >
          +
        </button>
        <button
          aria-label="지도 축소"
          disabled={zoom <= 0.8}
          onClick={() => setZoom((value) => Math.max(0.8, value - 0.2))}
        >
          −
        </button>
      </div>
    </>
  );
}
