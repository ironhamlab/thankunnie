import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useExploreStore } from '@/store/useExploreStore';
import ExploreMap from '@/components/map/ExploreMap';
import Button from '@/components/common/Button';
import styles from './HomeBeautyMap.module.css';

// 아래쪽 매장 패널과 위쪽 도구 막대에 핀이 가리지 않게 범위를 맞출 때 여백을 넓게 둔다
const MAP_BOUNDS_PADDING = { top: 70, right: 70, bottom: 120, left: 50 };

// 임박 빈자리 카드를 지도 핀 모양으로 바꾼다
function toPinShop(card) {
  return {
    shopId: card.shopId,
    shopName: card.shop,
    latitude: card.lat,
    longitude: card.lng,
    minSalePrice: card.price,
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
 */
export default function HomeBeautyMap({ focusCard, onHover, onExplore, onPickShop }) {
  const vacancies = useExploreStore((s) => s.imminentVacancies);
  const status = useExploreStore((s) => s.imminentStatus);
  const [selectedId, setSelectedId] = useState(null);
  const [saleOnly, setSaleOnly] = useState(false);
  const [mapUnavailable, setMapUnavailable] = useState(false);
  // 지도 초기화를 누를 때마다 바뀌어 지도를 새로 그린다
  const [fitVersion, setFitVersion] = useState(0);
  // 지금 지도로 옮겨 간 캐러셀 카드. 지도 초기화를 누르면 비운다
  const [imminent, setImminent] = useState(null);
  const [seenFocus, setSeenFocus] = useState(focusCard);
  // 캐러셀 카드가 바뀔 때마다 그 매장을 고른다. 렌더 중에 맞춰 효과로 한 번 더 그리지 않게 한다
  if (focusCard !== seenFocus) {
    setSeenFocus(focusCard);
    setImminent(focusCard ?? null);
    setSelectedId(focusCard ? String(focusCard.shopId) : null);
  }
  const focusPosition = useMemo(
    () => (imminent ? { lat: imminent.lat, lng: imminent.lng, zoom: true } : null),
    [imminent],
  );
  // 타임세일은 타임세일 화면과 같게 시작 3시간 이내 초임박 할인만 본다
  const cards = useMemo(
    () => (saleOnly ? vacancies.filter((card) => card.priceTier === 'LAST_MINUTE') : vacancies),
    [vacancies, saleOnly],
  );
  // 마감이 지나 목록을 다시 받으면 예전 카드가 빠질 수 있어, 지금 목록에 있는 카드만 쓴다
  const imminentNow = imminent
    ? vacancies.find((card) => card.shopId === imminent.shopId)
    : undefined;
  // 타임세일만 볼 때도 캐러셀이 보여주는 매장은 핀을 남긴다.
  // 핀 목록이 바뀌면 지도가 범위를 다시 맞추므로, 목록에 없는 매장일 때만 핀 목록을 바꾼다
  const extra =
    imminentNow && !cards.some((card) => card.shopId === imminentNow.shopId) ? imminentNow : null;
  const mapShops = useMemo(
    () => [...cards, ...(extra ? [extra] : [])].map(toPinShop),
    [cards, extra],
  );
  const selected = vacancies.find((card) => String(card.shopId) === selectedId) ?? cards[0];

  const handleUnavailable = useCallback(() => setMapUnavailable(true), []);
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
        <span>● 임박 빈자리 매장 {cards.length}곳</span>
        <button
          onClick={() => {
            setSelectedId(null);
            setImminent(null);
            setSaleOnly(false);
            setFitVersion((value) => value + 1);
          }}
        >
          ⌖ 지도 초기화
        </button>
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
            // 지도 초기화는 지도를 새로 그려 핀이 다 보이는 처음 범위로 돌린다
            key={fitVersion}
            shops={mapShops}
            selectedId={selectedId}
            onSelect={(shopId) => {
              setSelectedId(shopId);
              onPickShop?.(shopId);
            }}
            onUnavailable={handleUnavailable}
            fitToShops
            boundsPadding={MAP_BOUNDS_PADDING}
            focusPosition={focusPosition}
          />
        )}
        <div className={styles.toolbar}>
          <span>⌖ 왼쪽 임박 빈자리 매장만 보여줘요</span>
          <div>
            <button aria-pressed={!saleOnly} onClick={() => setSaleOnly(false)}>
              전체
            </button>
            <button aria-pressed={saleOnly} onClick={() => setSaleOnly(true)}>
              타임세일만
            </button>
          </div>
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
                    : saleOnly
                      ? '지금은 타임세일 빈자리가 없어요'
                      : '지금은 임박 빈자리가 없어요'}
              </strong>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
