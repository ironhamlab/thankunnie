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
 * 전체 날짜 보기는 빈자리가 없는 매장도 함께 받아, 그 매장은 빈자리 없음 안내와 매장 보기만 보여준다.
 * 초보 시술 모집 목록(model)이거나 초보 시술 빈자리만 있는 매장(modelOnly)이면 초보 시술 모집 상세로 간다.
 * 목록의 '지도에서 보기'로 고른 매장(pinnedShopId)은 펼친 매장보다 먼저 보여주고, 그 상태에서 핀을 누르면 고른 매장을 바꾼다.
 *
 * @param pinnedShopId  목록의 '지도에서 보기'로 고른 매장 ID (문자열)
 * @param onPinShop     지도에서 보기 상태에서 다른 핀을 누르면 그 매장 ID 를 받는다
 * @param focusPosition 목록에서 매장을 고를 때마다 새로 들어오는 { lat, lng, zoom }. 그 매장으로 지도를 확대해 옮긴다
 * @param myLocation    목록과 같이 쓰는 useMyLocation() 결과. 넘기면 지도에 내 위치 버튼을 보인다
 */
export default function ShopLocationMap({
  shops,
  status,
  expandedShopId,
  onShowShop,
  model,
  showTimes = true,
  pinnedShopId,
  onPinShop,
  focusPosition,
  myLocation,
}) {
  const [pickedId, setPickedId] = useState(null);
  const [mapUnavailable, setMapUnavailable] = useState(false);
  // 지도에서 보기로 고른 매장, 목록에서 펼친 매장, 지도에서 누른 매장 순으로 보여준다
  const chosen =
    shops.find((shop) => String(shop.shopId) === pinnedShopId) ??
    shops.find((shop) => String(shop.shopId) === expandedShopId) ??
    shops.find((shop) => String(shop.shopId) === pickedId);
  // 아무것도 고르지 않았으면 첫 매장을 아래 패널에 보여주되, 지도를 그 매장으로 옮기지는 않는다
  const selected = chosen ?? shops[0];
  const pick = (shopId) => {
    setPickedId(shopId);
    // 지도에서 보기로 고른 매장이 있으면 그것을 바꾸고, 다른 매장을 펼쳐 둔 상태라면 지도에서 고른 매장을 바로 펼친다
    if (pinnedShopId) onPinShop(shopId);
    else if (expandedShopId && shopId !== expandedShopId) onShowShop(shopId);
  };
  const handleUnavailable = useCallback(() => setMapUnavailable(true), []);
  const isExpanded = selected && String(selected.shopId) === expandedShopId;
  const hasVacancy = selected?.earliestStartsAt != null;
  return (
    <section className={styles.root} aria-label="매장 위치 지도">
      {mapUnavailable ? (
        <p className={styles.fallback}>지도를 불러오지 못했어요</p>
      ) : (
        <ExploreMap
          shops={shops}
          selectedId={selected ? String(selected.shopId) : null}
          autoSelected={!chosen}
          onSelect={pick}
          onUnavailable={handleUnavailable}
          fitToShops
          boundsPadding={MAP_BOUNDS_PADDING}
          focusPosition={focusPosition}
          myLocation={myLocation}
        />
      )}
      <span className={styles.count}>매장 {shops.length}곳</span>
      <div className={styles.panel}>
        {selected ? (
          <>
            <div>
              <strong>{selected.shopName}</strong>
              {hasVacancy ? (
                <p>
                  {earliestLabel(selected)}부터 · 빈자리 {selected.vacancyCount}개
                </p>
              ) : (
                <p className={styles.noVacancy}>지금은 빈자리가 없어요</p>
              )}
              <small>{selected.address}</small>
              {/* 일반·초보 시술이 섞인 매장은 매장 보기가 일반 시술로 가므로 초보 시술 빈자리는 따로 연결한다 */}
              {hasVacancy && !model && !selected.modelOnly && selected.modelVacancyCount > 0 && (
                <Link
                  className={styles.modelLink}
                  to={shopPagePath(selected.shopId, {
                    date: datePart(selected.earliestStartsAt),
                    model: true,
                  })}
                >
                  ✦ 초보 시술 모집 {selected.modelVacancyCount}개 ·{' '}
                  {money(selected.modelMinSalePrice)}~
                </Link>
              )}
            </div>
            <div className={styles.actions}>
              <Button
                as={Link}
                variant="primary"
                to={shopPagePath(selected.shopId, {
                  view: 'info',
                  // 상세정보에서 예약하기로 넘어가면 첫 영업일이 아니라 빈자리가 있는 날짜를 고른 채로 보여준다
                  date: hasVacancy ? datePart(selected.earliestStartsAt) : undefined,
                  model: hasVacancy && (model || selected.modelOnly),
                })}
              >
                매장 보기
              </Button>
              {showTimes && hasVacancy && (
                <Button
                  variant="soft"
                  aria-expanded={isExpanded}
                  onClick={() => onShowShop(isExpanded ? null : selected.shopId)}
                >
                  {isExpanded ? '시간 접기' : '시간 보기'}
                </Button>
              )}
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
