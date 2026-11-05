import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { formatMoney } from '@/utils/reservationFormat';
import {
  PIN_KIND,
  hasModelVacancy,
  modelLinkDate,
  modelVacancySuffix,
  pinKind,
  shopPagePath,
  vacancySummary,
} from '@/utils/exploreVacancy';
import { datePart } from '@/utils/scheduleDate';
import ExploreMap from '@/components/map/ExploreMap';
import Button from '@/components/common/Button';
import styles from './ShopLocationMap.module.css';

// 아래쪽 매장 패널과 오른쪽 확대 버튼에 핀이 가리지 않게 범위를 맞출 때 여백을 넓게 둔다
const MAP_BOUNDS_PADDING = { top: 90, right: 110, bottom: 150, left: 100 };
// 범례 순서와 이름. 지도 핀 색과 같은 점을 붙여 핀 뜻을 알려 주고, 누르면 그 종류만 지도에 남긴다.
// 좁은 휴대폰에서도 범례가 두 줄을 넘지 않게 칩에는 짧은 이름을 쓰고, 화면 읽기·마우스 설명에는 원래 이름을 쓴다
const LEGEND = [
  [PIN_KIND.LAST_MINUTE, '초임박', '초임박 특가'],
  [PIN_KIND.TODAY, '오늘', '오늘 빈자리'],
  [PIN_KIND.LATER, '다른 날', '다른 날 빈자리'],
  [PIN_KIND.MODEL, '초보', '초보 시술 빈자리'],
  [PIN_KIND.NONE, '마감', '빈자리 없음'],
];

/**
 * 검색 조건에 맞는 빈자리가 있는 매장 위치 (C-02). 목록의 매장이 다 보이게 범위를 맞추고, 축소하면 가까운 핀을 묶는다.
 * 목록에서 펼친 매장이 지도에서도 선택되고, 지도에서 고른 매장은 매장 페이지로 가거나 목록에서 펼쳐 시간을 볼 수 있다.
 * 전체 날짜 보기는 빈자리가 없는 매장도 함께 받아, 그 매장은 빈자리 없음 안내와 매장 보기만 보여준다.
 * 초보 시술 모집 목록(model)이거나 초보 시술 빈자리만 있는 매장(modelOnly)이면 초보 시술 모집 상세로 간다.
 * 목록 카드나 지도 핀으로 고른 매장(pinnedShopId)은 펼친 매장보다 먼저 보여주고, 목록에서도 그 카드를 표시한다.
 *
 * @param pinnedShopId  목록 카드나 지도 핀으로 고른 매장 ID (문자열)
 * @param onPinShop     펼친 매장이 없을 때 핀을 누르면 그 매장 ID 를 받는다
 * @param focusPosition 목록에서 매장을 고를 때마다 새로 들어오는 { lat, lng, zoom }. 그 매장으로 지도를 확대해 옮긴다
 * @param myLocation    목록과 같이 쓰는 useMyLocation() 결과. 넘기면 지도에 내 위치 버튼을 보인다
 * @param pageShopIds   목록 지금 페이지에 보이는 매장 ID(문자열) Set. 다른 페이지 매장 핀은 흐리게 보여준다
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
  pageShopIds,
  weekly = false,
}) {
  const [pickedId, setPickedId] = useState(null);
  const [kindFilter, setKindFilter] = useState(null);
  // 매장 수를 누를 때마다 바뀌어 지도가 매장 전체가 보이는 처음 범위로 돌아간다
  const [fitSignal, setFitSignal] = useState(0);
  // 지금 목록에 있는 핀 종류별 매장 수. 범례에는 있는 종류만 보여줘 페이지마다(타임세일·초보 시술 등) 맞는 것만 남는다
  const kindCounts = useMemo(() => {
    const counts = {};
    shops.forEach((shop) => {
      const kind = pinKind(shop);
      counts[kind] = (counts[kind] ?? 0) + 1;
    });
    return counts;
  }, [shops]);
  const legend = LEGEND.filter(([kind]) => kindCounts[kind]);
  // 조건이 바뀌어 고른 종류가 목록에 없어지면 다시 전체를 보여준다
  const activeKind = kindCounts[kindFilter] ? kindFilter : null;
  const mapShops = useMemo(
    () => (activeKind ? shops.filter((shop) => pinKind(shop) === activeKind) : shops),
    [shops, activeKind],
  );
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
    // 다른 매장을 펼쳐 둔 상태라면 지도에서 고른 매장을 바로 펼치고,
    // 그 밖에는 목록에서 그 매장 카드가 표시되도록 지도에서 고른 매장으로 정한다
    if (expandedShopId && !pinnedShopId) {
      if (shopId !== expandedShopId) onShowShop(shopId);
    } else {
      onPinShop(shopId);
    }
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
          shops={mapShops}
          activeIds={pageShopIds}
          selectedId={selected ? String(selected.shopId) : null}
          autoSelected={!chosen}
          onSelect={pick}
          onUnavailable={handleUnavailable}
          fitToShops
          boundsPadding={MAP_BOUNDS_PADDING}
          focusPosition={focusPosition}
          myLocation={myLocation}
          fitSignal={fitSignal}
          centerOnSelect
        />
      )}
      <div className={styles.legend}>
        {/* 매장 수는 전체 보기 버튼이다. 고른 종류를 풀고 지도를 처음 범위로 돌린다 */}
        <button
          type="button"
          className={`${styles.legendChip} ${styles.count}`}
          aria-pressed={!activeKind}
          aria-label={`전체 매장 ${shops.length}곳 보기`}
          title={`전체 매장 ${shops.length}곳 보기`}
          onClick={() => {
            setKindFilter(null);
            setFitSignal((value) => value + 1);
          }}
        >
          전체 {shops.length}
        </button>
        {/* 영업시간 기준이라 낮에는 모든 매장이 초임박 한 종류일 때가 많다. 한 종류여도 핀 색의 뜻을 알 수 있게 늘 보여준다 */}
        {legend.map(([kind, label, fullLabel]) => (
          <button
            key={kind}
            type="button"
            className={styles.legendChip}
            aria-pressed={activeKind === kind}
            aria-label={`${fullLabel} 매장 ${kindCounts[kind]}곳`}
            title={`${fullLabel} 매장 ${kindCounts[kind]}곳`}
            onClick={() => setKindFilter(activeKind === kind ? null : kind)}
          >
            <span className={`${styles.legendDot} ${styles[kind]}`} aria-hidden="true" />
            {label} {kindCounts[kind]}
          </button>
        ))}
      </div>
      <div className={styles.panel}>
        {selected ? (
          <>
            <div>
              <strong>{selected.shopName}</strong>
              {hasVacancy ? (
                <p>{vacancySummary(selected, { weekly })}</p>
              ) : (
                <p className={styles.noVacancy}>지금은 빈자리가 없어요</p>
              )}
              <small>{selected.address}</small>
              {/* 일반·초보 시술이 섞인 매장은 매장 보기가 일반 시술로 가므로 초보 시술 빈자리는 따로 연결한다 */}
              {hasVacancy && !model && !selected.modelOnly && hasModelVacancy(selected) && (
                <Link
                  className={styles.modelLink}
                  to={shopPagePath(selected.shopId, {
                    date: modelLinkDate(selected),
                    model: true,
                  })}
                >
                  ✦ 초보 시술 모집
                  {modelVacancySuffix(selected, { weekly })} ·{' '}
                  {formatMoney(selected.modelMinSalePrice)}~
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
