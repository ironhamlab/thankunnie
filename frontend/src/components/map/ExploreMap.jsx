import { useEffect, useRef, useState } from 'react';
import Spinner from '@/components/common/Spinner';
import { useMyLocation } from '@/hooks/useMyLocation';
import { money } from '@/utils/demoData';
import { hasKakaoMapKey, loadKakaoMap } from '@/utils/kakaoMapLoader';
import { logger } from '@/utils/logger';
import styles from './ExploreMap.module.css';

// 처음 열 때 위치 (강남역). 위치 권한은 처음에 묻지 않고 "내 위치" 버튼을 눌렀을 때만 묻는다
const DEFAULT_CENTER = { lat: 37.4979, lng: 127.0276 };
const DEFAULT_LEVEL = 6;
// 목록에서 고른 매장으로 갈 때 확대 단계. 묶음(CLUSTER_MIN_LEVEL)이 풀리고 주변 길까지 보이는 정도다
const FOCUS_LEVEL = 4;
// 지도를 움직이다 멈춘 뒤 이만큼 기다렸다가 마지막 범위로만 조회한다
const IDLE_DELAY_MS = 300;
// 이 확대 단계 이상(숫자가 클수록 넓게 보인다)에서 가까운 핀을 하나로 묶는다
const CLUSTER_MIN_LEVEL = 6;
const CLUSTER_GRID_PX = 70;
// 위경도는 소수 6자리(약 10cm)면 충분하고, 같은 범위를 같은 값으로 보내야 불필요한 재조회가 줄어든다
const COORD_DIGITS = 6;
// 묶음 동그라미 모양. 카카오 클러스터러가 요소에 직접 넣는 인라인 스타일이다
const CLUSTER_STYLE = {
  width: '64px',
  height: '64px',
  borderRadius: '50%',
  background: 'var(--color-primary)',
  border: '3px solid white',
  boxShadow: '0 2px 8px rgba(0, 0, 0, 0.25)',
  color: 'white',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  justifyContent: 'center',
  lineHeight: '1.2',
  fontSize: '11px',
  fontWeight: '700',
  textAlign: 'center',
};

// 매장이 다 보이게 범위를 맞출 때 기본 여백(px)
const DEFAULT_FIT_PADDING = { top: 40, right: 40, bottom: 40, left: 40 };
// 매장이 서울·경기에 흩어져 있으면 다 보이게 맞춘 범위가 너무 넓다. 동네가 보이는 이 단계까지만 넓힌다
const MAX_FIT_LEVEL = 7;

const round = (value) => Number(value.toFixed(COORD_DIGITS));

/**
 * 지도 범위로 조회하는 지도 (홈 지도, 지도 화면 /map). 지도 범위가 바뀌면 그 범위를 알려 다시 조회하게 하고,
 * 받은 매장을 "최저가~" 핀으로 찍는다. 축소하면 가까운 핀을 "N곳 · 최저가~" 묶음으로 합치고, 묶음을 누르면 확대한다.
 * 범위 조회 결과로 지도 범위를 다시 맞추지 않는다(맞추면 다시 조회가 일어나 지도가 계속 움직인다).
 * "내 위치"를 누르면 브라우저 위치로 지도를 옮기고 점을 찍는다. 위치는 서버로 보내지 않고 지도 범위만 보낸다.
 *
 * @param shops          매장 요약(C-02) 목록. 빈자리가 없는 매장(최저가 없음)은 매장 이름 핀으로 찍는다
 * @param selectedId     선택한 매장 ID (문자열)
 * @param autoSelected   true 면 selectedId 가 사용자가 고른 게 아니라 기본으로 정한 매장이다. 핀만 강조하고, 화면 밖이어도 옮기지 않는다
 * @param onSelect       핀을 누르면 매장 ID 를 받는다
 * @param onBoundsChange 지도 범위 { minLat, maxLat, minLng, maxLng } 를 받는다
 * @param onUnavailable  키가 없거나 SDK 를 불러오지 못했을 때 부른다
 * @param resetSignal    바뀔 때마다 지도를 처음 위치로 돌린다 (지도 초기화 버튼). 0 이면 무시한다
 * @param focusPosition  { lat, lng, zoom } 가 들어오면 그 위치로 지도를 옮긴다 (새로고침 때 고른 매장 위치 복원).
 *                       zoom 이 true 면 묶음이 풀리도록 가까이 확대한다 (목록에서 고른 매장 보여주기)
 * @param fitToShops     true 면 받은 매장이 다 보이게 범위를 맞춘다 (검색 화면처럼 목록이 지도 범위와 상관없는 곳).
 *                       이때는 onBoundsChange 를 넘기지 않아 지도를 움직여도 다시 조회하지 않는다.
 *                       매장이 넓게 흩어져 있으면 MAX_FIT_LEVEL 까지만 넓히고 목록 맨 앞 매장 주변을 보여준다
 * @param boundsPadding  fitToShops 로 범위를 맞출 때 띄울 여백(px). 지도 위에 패널이 겹치는 쪽을 넓게 준다
 * @param myLocation     useMyLocation() 결과. 넘기면 내 위치를 화면과 같이 써서(목록 거리 표시 등) 내 위치 버튼을 보인다.
 *                       넘기지 않으면 지도 혼자 위치를 확인하고, 다시 조회하지 않는 지도(fitToShops)에는 버튼을 숨긴다
 */
export default function ExploreMap({
  shops,
  selectedId,
  autoSelected = false,
  onSelect,
  onBoundsChange,
  onUnavailable,
  resetSignal = 0,
  focusPosition = null,
  fitToShops = false,
  boundsPadding = DEFAULT_FIT_PADDING,
  myLocation,
}) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const clustererRef = useRef(null);
  const elementsRef = useRef(new Map());
  // 고른 핀을 이웃 핀 위로 올리려고 매장 ID → 오버레이를 기억해 둔다
  const overlaysRef = useRef(new Map());
  // 묶음에 최저가를 쓰려고 핀(오버레이) → 최저가를 기억해 둔다
  const pricesRef = useRef(new Map());
  const callbacksRef = useRef({ onSelect, onBoundsChange, onUnavailable });
  const [status, setStatus] = useState(hasKakaoMapKey() ? 'loading' : 'error');
  const myLocationRef = useRef(null);
  const ownLocation = useMyLocation();
  const {
    position: myPosition,
    locating,
    message: locateMessage,
    locate,
  } = myLocation ?? ownLocation;
  const showLocate = Boolean(onBoundsChange || myLocation);

  useEffect(() => {
    callbacksRef.current = { onSelect, onBoundsChange, onUnavailable };
  }, [onSelect, onBoundsChange, onUnavailable]);

  useEffect(() => {
    if (!hasKakaoMapKey()) {
      callbacksRef.current.onUnavailable?.();
      return undefined;
    }
    let cancelled = false;
    let idleTimer;
    const container = containerRef.current;
    loadKakaoMap()
      .then((maps) => {
        if (cancelled) return;
        const map = new maps.Map(container, {
          center: new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
          level: DEFAULT_LEVEL,
        });
        map.addControl(new maps.ZoomControl(), maps.ControlPosition.RIGHT);
        const clusterer = new maps.MarkerClusterer({
          map,
          averageCenter: true,
          minLevel: CLUSTER_MIN_LEVEL,
          gridSize: CLUSTER_GRID_PX,
          styles: [CLUSTER_STYLE],
        });
        // 기본 묶음은 개수만 보여 주므로 묶인 매장의 최저가를 함께 쓴다
        maps.event.addListener(clusterer, 'clustered', (clusters) => {
          clusters.forEach((cluster) => {
            const overlays = cluster.getMarkers();
            // 빈자리가 없는 매장만 묶이면 최저가 없이 개수만 보여 준다
            const prices = overlays
              .map((overlay) => pricesRef.current.get(overlay))
              .filter((price) => price != null);
            const minPrice = prices.length ? money(Math.min(...prices)) : null;
            const content = cluster.getClusterMarker().getContent();
            if (content instanceof HTMLElement) {
              content.innerHTML = `<span>${overlays.length}곳</span>${minPrice ? `<span>${minPrice}~</span>` : ''}`;
              content.setAttribute(
                'aria-label',
                `매장 ${overlays.length}곳${minPrice ? `, ${minPrice}부터` : ''}`,
              );
            }
          });
        });
        const reportBounds = () => {
          window.clearTimeout(idleTimer);
          idleTimer = window.setTimeout(() => {
            const bounds = map.getBounds();
            const sw = bounds.getSouthWest();
            const ne = bounds.getNorthEast();
            callbacksRef.current.onBoundsChange?.({
              minLat: round(sw.getLat()),
              maxLat: round(ne.getLat()),
              minLng: round(sw.getLng()),
              maxLng: round(ne.getLng()),
            });
          }, IDLE_DELAY_MS);
        };
        maps.event.addListener(map, 'idle', reportBounds);
        mapRef.current = map;
        clustererRef.current = clusterer;
        setStatus('ready');
        // 처음 만들 때는 idle 이 오지 않으므로 첫 범위는 직접 알린다
        reportBounds();
      })
      .catch((error) => {
        logger.error(error);
        if (cancelled) return;
        setStatus('error');
        callbacksRef.current.onUnavailable?.();
      });
    return () => {
      cancelled = true;
      window.clearTimeout(idleTimer);
      clustererRef.current?.clear();
      mapRef.current = null;
      clustererRef.current = null;
      // 카카오맵에는 해제 API가 없어 다시 마운트될 때 지도가 겹치지 않도록 비운다
      container.replaceChildren();
    };
  }, []);

  useEffect(() => {
    const clusterer = clustererRef.current;
    if (status !== 'ready' || !clusterer) return;
    const { maps } = window.kakao;
    const elements = elementsRef.current;
    const overlaysById = overlaysRef.current;
    const prices = pricesRef.current;
    elements.clear();
    overlaysById.clear();
    prices.clear();
    const overlays = shops.map((shop) => {
      const element = document.createElement('button');
      element.type = 'button';
      const hasVacancy = shop.minSalePrice != null;
      element.className = hasVacancy ? styles.pin : `${styles.pin} ${styles.pinNoVacancy}`;
      element.textContent = hasVacancy ? `${money(shop.minSalePrice)}~` : shop.shopName;
      element.setAttribute(
        'aria-label',
        hasVacancy
          ? `${shop.shopName} ${money(shop.minSalePrice)}부터`
          : `${shop.shopName} 빈자리 없음`,
      );
      element.addEventListener('click', () => callbacksRef.current.onSelect?.(String(shop.shopId)));
      elements.set(String(shop.shopId), element);
      const overlay = new maps.CustomOverlay({
        position: new maps.LatLng(shop.latitude, shop.longitude),
        content: element,
        yAnchor: 1.3,
      });
      prices.set(overlay, shop.minSalePrice);
      overlaysById.set(String(shop.shopId), overlay);
      return overlay;
    });
    clusterer.addMarkers(overlays);
    const map = mapRef.current;
    if (fitToShops && map && shops.length > 1) {
      const bounds = new maps.LatLngBounds();
      shops.forEach((shop) => bounds.extend(new maps.LatLng(shop.latitude, shop.longitude)));
      const { top, right, bottom, left } = boundsPadding;
      map.setBounds(bounds, top, right, bottom, left);
      // 줄인 범위로 매장들 가운데를 보면 핀이 하나도 없을 수 있어, 목록 맨 앞 매장을 가운데에 둔다
      if (map.getLevel() > MAX_FIT_LEVEL) {
        map.setLevel(MAX_FIT_LEVEL);
        map.setCenter(new maps.LatLng(shops[0].latitude, shops[0].longitude));
      }
    } else if (fitToShops && map && shops.length === 1) {
      map.setCenter(new maps.LatLng(shops[0].latitude, shops[0].longitude));
    }
    return () => clusterer.clear();
  }, [status, shops, fitToShops, boundsPadding]);

  useEffect(() => {
    if (status !== 'ready') return;
    elementsRef.current.forEach((element, id) => {
      const selected = id === selectedId;
      element.classList.toggle('is-selected', selected);
      element.setAttribute('aria-pressed', String(selected));
      overlaysRef.current.get(id)?.setZIndex(selected ? 2 : 0);
    });
    // 목록에서 고른 매장이 화면 밖에 있을 때만 옮긴다. 화면 안이면 그대로 둬야 다시 조회가 일어나지 않는다.
    // 그 매장으로 확대해 옮기는 중(focusPosition.zoom)이면 천천히 옮기는 panTo 가 확대 이동을 덮어쓰므로 건너뛴다.
    // 기본으로 정한 매장은 범위를 MAX_FIT_LEVEL 로 줄였을 때 화면 밖일 수 있는데, 그때 옮기면 매장들 가운데를 보여줄 수 없다
    if (autoSelected) return;
    const shop = shops.find((item) => String(item.shopId) === selectedId);
    const map = mapRef.current;
    const focusing =
      focusPosition?.zoom &&
      focusPosition.lat === shop?.latitude &&
      focusPosition.lng === shop?.longitude;
    if (shop && map && !focusing) {
      const position = new window.kakao.maps.LatLng(shop.latitude, shop.longitude);
      if (!map.getBounds().contain(position)) map.panTo(position);
    }
  }, [status, shops, selectedId, autoSelected, focusPosition]);

  // 지도 초기화: 값이 바뀔 때마다 처음 위치·확대 단계로 돌린다. 옮기면 idle 로 그 범위를 다시 조회한다
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map || !resetSignal) return;
    map.setLevel(DEFAULT_LEVEL);
    map.setCenter(new window.kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng));
  }, [status, resetSignal]);

  // 옮기면 idle 로 그 범위를 다시 조회한다. zoom 이 없으면 확대 단계는 그대로 둔다
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map || !focusPosition) return;
    if (focusPosition.zoom) map.setLevel(FOCUS_LEVEL);
    map.setCenter(new window.kakao.maps.LatLng(focusPosition.lat, focusPosition.lng));
  }, [status, focusPosition]);

  // 내 위치로 지도를 옮기면 idle 로 그 주변을 다시 조회한다
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map || !myPosition) return;
    const { maps } = window.kakao;
    const position = new maps.LatLng(myPosition.lat, myPosition.lng);
    if (myLocationRef.current) {
      myLocationRef.current.setPosition(position);
    } else {
      const dot = document.createElement('span');
      dot.className = styles.myLocation;
      dot.setAttribute('aria-label', '내 위치');
      myLocationRef.current = new maps.CustomOverlay({
        map,
        position,
        content: dot,
        zIndex: 3,
      });
    }
    map.setLevel(DEFAULT_LEVEL);
    map.setCenter(position);
  }, [status, myPosition]);

  if (status === 'error') return null;
  return (
    <div className={styles.wrapper}>
      <div ref={containerRef} className={styles.map} />
      {status === 'loading' && (
        <div className={styles.loading}>
          <Spinner />
        </div>
      )}
      {/* 다시 조회하지 않는 지도(범위 맞추기 모드)는 화면이 위치를 같이 쓸 때만 보인다 */}
      {status === 'ready' && showLocate && (
        <button
          type="button"
          className={styles.locate}
          onClick={locate}
          disabled={locating}
          aria-label="내 위치로 이동"
        >
          <span aria-hidden="true">⌖</span>
          {locating ? '찾는 중…' : '내 위치'}
        </button>
      )}
      {locateMessage && (
        <p className={styles.locateMessage} role="status">
          {locateMessage}
        </p>
      )}
    </div>
  );
}
