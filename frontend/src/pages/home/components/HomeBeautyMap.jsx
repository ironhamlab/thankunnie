import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useMyLocation } from '@/hooks/useMyLocation';
import { useExploreStore } from '@/store/useExploreStore';
import ExploreMap from '@/components/map/ExploreMap';
import Button from '@/components/common/Button';
import styles from './HomeBeautyMap.module.css';

// 아래쪽 매장 패널과 위쪽 도구 막대에 핀이 가리지 않게 범위를 맞출 때 여백을 넓게 둔다
const MAP_BOUNDS_PADDING = { top: 70, right: 70, bottom: 120, left: 50 };
// 캐러셀 카드 매장으로 옮길 때 확대 단계. 매장 하나만 크게 보이지 않고 주변 매장 핀도 보이게 목록 지도보다 한 단계 넓힌다
const FOCUS_LEVEL = 5;

// 임박 빈자리 카드를 지도 핀 모양(C-02 매장 요약과 같은 이름)으로 바꾼다. 핀 종류도 카드의 시작 시각·가격 구간으로 정한다
function toPinShop(card) {
  return {
    shopId: card.shopId,
    shopName: card.shop,
    latitude: card.lat,
    longitude: card.lng,
    minSalePrice: card.price,
    earliestStartsAt: card.startsAt,
    earliestPriceTier: card.priceTier,
    maxDiscountRate: card.discount,
  };
}

/**
 * 홈 지도. 왼쪽 캐러셀과 같은 임박 빈자리 매장만 핀으로 찍고, 처음에는 그 매장들이 다 보이게 범위를 맞춘다.
 * 빈자리가 있는 매장 전체는 지도 크게 보기(/map)에서 본다.
 * 캐러셀에 지금 보이는 임박 빈자리(focusCard)가 바뀌면 그 매장으로 옮겨 고르고, 아래 패널에 그 시간을 보여준다.
 * 지도를 보는 동안 캐러셀이 넘어가 지도가 옮겨 가지 않게, 마우스를 올리거나 지도를 만지면 알린다.
 *
 * @param focusCard   캐러셀에 지금 보이는 임박 빈자리 카드
 * @param onHover     마우스를 올리면 true, 내리면 false 를 받는다
 * @param onExplore   지도를 끌거나 누르거나 확대하는 등 직접 만지면 불린다. 끄는 동안에는 계속 불린다
 * @param onPickShop  핀을 누르면 그 매장 ID(문자열)를 받는다. 캐러셀을 그 매장 카드로 넘길 때 쓴다
 * @param onLocated   내 위치로 옮기면 불린다. 저절로 넘어가는 캐러셀이 지도를 다시 옮기지 않게 할 때 쓴다
 */
export default function HomeBeautyMap({ focusCard, onHover, onExplore, onPickShop, onLocated }) {
  const vacancies = useExploreStore((s) => s.imminentVacancies);
  const status = useExploreStore((s) => s.imminentStatus);
  const [selectedId, setSelectedId] = useState(null);
  const [mapUnavailable, setMapUnavailable] = useState(false);
  const location = useMyLocation();
  // 지금 지도로 옮겨 간 캐러셀 카드. 내 위치로 옮기면 비운다
  const [imminent, setImminent] = useState(null);
  const [seenFocus, setSeenFocus] = useState(focusCard);
  // 캐러셀 카드가 바뀔 때마다 그 매장을 고른다. 렌더 중에 맞춰 효과로 한 번 더 그리지 않게 한다
  if (focusCard !== seenFocus) {
    setSeenFocus(focusCard);
    setImminent(focusCard ?? null);
    setSelectedId(focusCard ? String(focusCard.shopId) : null);
  }
  const focusPosition = useMemo(
    () =>
      imminent ? { lat: imminent.lat, lng: imminent.lng, zoom: true, level: FOCUS_LEVEL } : null,
    [imminent],
  );
  // 임박 빈자리가 없어 스토어가 다음으로 빠른 빈자리를 대신 담았으면 문구에서 "임박"을 뺀다
  const upcoming = vacancies.length > 0 && vacancies.every((card) => card.upcoming);
  const listName = upcoming ? '빠른 빈자리' : '임박 빈자리';
  // 초임박 특가는 핀 모양(번개)으로 따로 보이므로 따로 거르지 않고 캐러셀과 같은 매장을 모두 찍는다
  const mapShops = useMemo(() => vacancies.map(toPinShop), [vacancies]);
  const selected = vacancies.find((card) => String(card.shopId) === selectedId) ?? vacancies[0];

  const handleUnavailable = useCallback(() => setMapUnavailable(true), []);
  // 내 위치로 옮기면 고른 매장을 풀어, 그 매장으로 지도가 되돌아가지 않게 한다
  const locate = async () => {
    const found = await location.locate();
    if (found) {
      setSelectedId(null);
      setImminent(null);
      onLocated?.();
    }
    return found;
  };
  return (
    <div
      className={styles.section}
      // 터치는 손을 떼도 나갔다는 신호가 없으므로 마우스일 때만 본다
      onPointerEnter={(event) => event.pointerType === 'mouse' && onHover?.(true)}
      onPointerLeave={(event) => event.pointerType === 'mouse' && onHover?.(false)}
      onPointerDown={() => onExplore?.()}
      // 끄는 동안에도 계속 알려, 오래 끌어도 도중에 캐러셀이 넘어가지 않게 한다
      onPointerMove={(event) => (event.buttons || event.pointerType !== 'mouse') && onExplore?.()}
      onWheel={() => onExplore?.()}
    >
      <div className={styles.heading}>
        <h2>실시간 빈자리 지도</h2>
        <span>
          ● {listName} 매장 {vacancies.length}곳
        </span>
        <Link className={styles.mapLink} to={ROUTES.MAP}>
          지도 크게 보기 ›
        </Link>
      </div>
      <div className={styles.root}>
        {mapUnavailable ? (
          <small className={styles.disclaimer}>
            지도를 불러오지 못했어요 · 목록에서 확인해 주세요
          </small>
        ) : (
          <ExploreMap
            shops={mapShops}
            myLocation={{ ...location, locate }}
            selectedId={selectedId}
            onSelect={(shopId) => {
              setSelectedId(shopId);
              onPickShop?.(shopId);
            }}
            onUnavailable={handleUnavailable}
            fitToShops
            // 임박 목록을 주기적으로 다시 받으므로 그때마다 보던 범위가 돌아가지 않게 한다
            fitOnce
            centerOnSelect
            boundsPadding={MAP_BOUNDS_PADDING}
            focusPosition={focusPosition}
          />
        )}
        <div className={styles.toolbar}>
          <span>⌖ 왼쪽 {listName} 매장만 보여줘요</span>
        </div>
        <div className={styles.preview}>
          <span className={styles.storeIcon} aria-hidden="true">
            ▤
          </span>
          {selected ? (
            <>
              <div>
                <strong>{selected.shop}</strong>
                <span>
                  {selected.day} {selected.time} 시작
                </span>
                <p>
                  {selected.location} · {selected.name}
                </p>
              </div>
              <Button as={Link} variant="primary" to={selected.shopPath}>
                이 시간 예약하기
              </Button>
            </>
          ) : (
            <div>
              <strong>
                {status === 'loading' || status === 'idle'
                  ? '임박 빈자리를 찾는 중이에요'
                  : status === 'failed'
                    ? '빈자리를 불러오지 못했어요'
                    : '지금은 임박 빈자리가 없어요'}
              </strong>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
