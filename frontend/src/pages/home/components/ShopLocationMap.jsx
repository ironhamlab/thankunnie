import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { money } from '@/utils/demoData';
import { earliestLabel, shopPagePath } from '@/utils/exploreVacancy';
import { datePart } from '@/utils/scheduleDate';
import KakaoMap from '@/components/map/KakaoMap';
import Button from '@/components/common/Button';
import styles from './ShopLocationMap.module.css';

// 아래쪽 매장 패널과 오른쪽 확대 버튼에 핀이 가리지 않게 범위를 맞출 때 여백을 넓게 둔다
const MAP_BOUNDS_PADDING = { top: 60, right: 110, bottom: 150, left: 100 };

/**
 * 검색 조건에 맞는 빈자리가 있는 매장 위치 (C-02). 목록에서 펼친 매장이 지도에서도 선택되고,
 * 지도에서 고른 매장은 매장 페이지로 가거나 목록에서 펼쳐 시간을 볼 수 있다.
 */
export default function ShopLocationMap({ shops, status, expandedShopId, onShowShop }) {
  const [pickedId, setPickedId] = useState(null);
  // 목록에서 펼친 매장을 우선 보여주고, 그다음 지도에서 누른 매장을 보여준다
  const selected =
    shops.find((shop) => String(shop.shopId) === expandedShopId) ??
    shops.find((shop) => shop.shopId === pickedId) ??
    shops[0];
  const markers = useMemo(
    () =>
      shops.map((shop) => ({
        id: shop.shopId,
        lat: shop.latitude,
        lng: shop.longitude,
        label: money(shop.minSalePrice),
        ariaLabel: `${shop.shopName} ${money(shop.minSalePrice)}부터`,
      })),
    [shops],
  );
  const pick = (shopId) => {
    setPickedId(shopId);
    // 다른 매장을 펼쳐 둔 상태라면 지도에서 고른 매장을 바로 펼친다
    if (expandedShopId && String(shopId) !== expandedShopId) onShowShop(shopId);
  };
  const isExpanded = selected && String(selected.shopId) === expandedShopId;
  return (
    <section className={styles.root} aria-label="매장 위치 지도">
      <KakaoMap
        markers={markers}
        selectedId={selected?.shopId}
        onSelect={pick}
        markerClassName={styles.pin}
        boundsPadding={MAP_BOUNDS_PADDING}
        fallback={<p className={styles.fallback}>지도를 불러오지 못했어요</p>}
      />
      <span className={styles.count}>매장 {shops.length}곳</span>
      <div className={styles.panel}>
        {selected ? (
          <>
            <div>
              <strong>{selected.shopName}</strong>
              <p>
                {earliestLabel(selected)}부터 · 빈자리 {selected.vacancyCount}개
              </p>
              <small>{selected.address}</small>
            </div>
            <div className={styles.actions}>
              <Button
                as={Link}
                variant="primary"
                to={shopPagePath(selected.shopId, {
                  date: datePart(selected.earliestStartsAt),
                })}
              >
                매장 보기
              </Button>
              <Button
                variant="soft"
                aria-expanded={isExpanded}
                onClick={() => onShowShop(isExpanded ? null : selected.shopId)}
              >
                {isExpanded ? '시간 접기' : '시간 보기'}
              </Button>
            </div>
          </>
        ) : (
          <strong>
            {status === 'loading' ? '매장을 찾는 중이에요' : '조건에 맞는 매장이 없어요'}
          </strong>
        )}
      </div>
    </section>
  );
}
