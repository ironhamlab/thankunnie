import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { money } from '@/utils/demoData';
import { earliestLabel, shopPagePath } from '@/utils/exploreVacancy';
import { datePart } from '@/utils/scheduleDate';
import ExploreMap from '@/components/map/ExploreMap';
import Button from '@/components/common/Button';
import styles from './ShopLocationMap.module.css';

// 아래쪽 매장 패널과 오른쪽 확대 버튼에 핀이 가리지 않게 범위를 맞출 때 여백을 넓게 둔다
const MAP_BOUNDS_PADDING = { top: 60, right: 110, bottom: 150, left: 100 };

/**
 * 검색 조건에 맞는 빈자리가 있는 매장 위치 (C-02). 목록의 매장이 다 보이게 범위를 맞추고, 축소하면 가까운 핀을 묶는다.
 * 목록에서 펼친 매장이 지도에서도 선택되고, 지도에서 고른 매장은 매장 페이지로 가거나 목록에서 펼쳐 시간을 볼 수 있다.
 * 모델 모집 목록(model)이거나 모델 시술 빈자리만 있는 매장(modelOnly)이면 모델 모집 상세로 간다.
 */
export default function ShopLocationMap({ shops, status, expandedShopId, onShowShop, model }) {
  const [pickedId, setPickedId] = useState(null);
  const [mapUnavailable, setMapUnavailable] = useState(false);
  // 목록에서 펼친 매장을 우선 보여주고, 그다음 지도에서 누른 매장을 보여준다
  const selected =
    shops.find((shop) => String(shop.shopId) === expandedShopId) ??
    shops.find((shop) => String(shop.shopId) === pickedId) ??
    shops[0];
  const pick = (shopId) => {
    setPickedId(shopId);
    // 다른 매장을 펼쳐 둔 상태라면 지도에서 고른 매장을 바로 펼친다
    if (expandedShopId && shopId !== expandedShopId) onShowShop(shopId);
  };
  const handleUnavailable = useCallback(() => setMapUnavailable(true), []);
  const isExpanded = selected && String(selected.shopId) === expandedShopId;
  return (
    <section className={styles.root} aria-label="매장 위치 지도">
      {mapUnavailable ? (
        <p className={styles.fallback}>지도를 불러오지 못했어요</p>
      ) : (
        <ExploreMap
          shops={shops}
          selectedId={selected ? String(selected.shopId) : null}
          onSelect={pick}
          onUnavailable={handleUnavailable}
          fitToShops
          boundsPadding={MAP_BOUNDS_PADDING}
        />
      )}
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
              {/* 일반·모델이 섞인 매장은 매장 보기가 일반 시술로 가므로 모델 빈자리는 따로 연결한다 */}
              {!model && !selected.modelOnly && selected.modelVacancyCount > 0 && (
                <Link
                  className={styles.modelLink}
                  to={shopPagePath(selected.shopId, {
                    date: datePart(selected.earliestStartsAt),
                    model: true,
                  })}
                >
                  ✦ 모델 모집 빈자리 {selected.modelVacancyCount}개 ·{' '}
                  {money(selected.modelMinSalePrice)}~ ›
                </Link>
              )}
            </div>
            <div className={styles.actions}>
              <Button
                as={Link}
                variant="primary"
                to={shopPagePath(selected.shopId, {
                  date: datePart(selected.earliestStartsAt),
                  model: model || selected.modelOnly,
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
