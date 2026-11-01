import { useEffect, useRef, useState } from 'react';
import Spinner from '@/components/common/Spinner';
import { hasKakaoMapKey, loadKakaoMap } from '@/utils/kakaoMapLoader';
import { logger } from '@/utils/logger';
import styles from './KakaoMap.module.css';

// 마커가 없을 때 보여줄 기본 위치 (강남역)
const DEFAULT_CENTER = { lat: 37.4979, lng: 127.0276 };
const DEFAULT_LEVEL = 5;
const DEFAULT_BOUNDS_PADDING = { top: 40, right: 40, bottom: 40, left: 40 };

/**
 * 카카오맵 위에 가격 등 라벨 마커를 띄우는 지도.
 * 키가 없거나 SDK를 불러오지 못하면 fallback을 대신 보여준다.
 *
 * @param markers         [{ id, lat, lng, label, ariaLabel }] — 렌더마다 새 배열을 넘기지 않도록 useMemo로 고정
 * @param selectedId      선택된 마커 id
 * @param onSelect        마커 클릭 시 id를 받는 콜백
 * @param markerClassName 마커 버튼 클래스. 선택된 마커에는 is-selected가 추가된다
 * @param boundsPadding   마커 범위를 맞출 때 띄울 여백(px). 지도 위에 패널이 겹치는 쪽을 넓게 준다
 * @param level           마커가 1개일 때 확대 정도(작을수록 가깝게). 생략하면 기본 확대를 유지한다
 * @param fallback        지도를 쓸 수 없을 때 보여줄 요소
 */
export default function KakaoMap({
  markers,
  selectedId,
  onSelect,
  markerClassName,
  boundsPadding = DEFAULT_BOUNDS_PADDING,
  level,
  fallback = null,
}) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const markerElementsRef = useRef(new Map());
  const onSelectRef = useRef(onSelect);
  const prevSelectedIdRef = useRef(undefined);
  const [status, setStatus] = useState(hasKakaoMapKey() ? 'loading' : 'error');

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  useEffect(() => {
    if (!hasKakaoMapKey()) {
      return undefined;
    }
    let cancelled = false;
    let resizeObserver = null;
    const container = containerRef.current;
    loadKakaoMap()
      .then((maps) => {
        if (cancelled) {
          return;
        }
        const map = new maps.Map(container, {
          center: new maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
          level: DEFAULT_LEVEL,
        });
        map.addControl(new maps.ZoomControl(), maps.ControlPosition.RIGHT);
        mapRef.current = map;
        // 만든 뒤 상자 크기가 바뀌면(화면 폭·탭 전환) 카카오맵이 크기를 다시 재지 않아 일부만 그려지므로,
        // 보던 가운데를 기억했다가 다시 재고 되돌린다
        resizeObserver = new ResizeObserver(() => {
          const center = map.getCenter();
          map.relayout();
          map.setCenter(center);
        });
        resizeObserver.observe(container);
        setStatus('ready');
      })
      .catch((error) => {
        logger.error(error);
        if (!cancelled) {
          setStatus('error');
        }
      });
    return () => {
      cancelled = true;
      resizeObserver?.disconnect();
      mapRef.current = null;
      // 카카오맵에는 해제 API가 없어 다시 마운트될 때 지도가 겹치지 않도록 비운다
      container.replaceChildren();
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map) {
      return undefined;
    }
    const { maps } = window.kakao;
    const bounds = new maps.LatLngBounds();
    const elements = markerElementsRef.current;
    const overlays = markers.map((marker) => {
      const position = new maps.LatLng(marker.lat, marker.lng);
      const element = document.createElement('button');
      element.type = 'button';
      element.className = markerClassName ?? '';
      element.textContent = marker.label;
      element.setAttribute('aria-label', marker.ariaLabel ?? marker.label);
      element.addEventListener('click', () => onSelectRef.current?.(marker.id));
      elements.set(marker.id, element);
      bounds.extend(position);
      return new maps.CustomOverlay({ map, position, content: element, yAnchor: 1.3 });
    });
    if (markers.length > 1) {
      const { top, right, bottom, left } = boundsPadding;
      map.setBounds(bounds, top, right, bottom, left);
    } else if (markers.length === 1) {
      map.setCenter(new maps.LatLng(markers[0].lat, markers[0].lng));
    }
    return () => {
      overlays.forEach((overlay) => overlay.setMap(null));
      elements.clear();
    };
  }, [status, markers, markerClassName, boundsPadding]);

  // 확대 단계는 마커와 따로 맞춘다. 마커 effect 에 넣으면 단계가 바뀔 때 마커를 다시 만들어 선택 표시가 사라진다
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map || !level || markers.length !== 1) {
      return;
    }
    map.setLevel(level);
    map.setCenter(new window.kakao.maps.LatLng(markers[0].lat, markers[0].lng));
  }, [status, markers, level]);

  useEffect(() => {
    if (status !== 'ready') {
      return;
    }
    markerElementsRef.current.forEach((element, id) => {
      const selected = id === selectedId;
      element.classList.toggle('is-selected', selected);
      element.setAttribute('aria-pressed', String(selected));
    });
    // 처음 선택값으로는 이동하지 않아야 setBounds로 맞춘 전체 범위가 유지된다
    const changed =
      prevSelectedIdRef.current !== undefined && prevSelectedIdRef.current !== selectedId;
    prevSelectedIdRef.current = selectedId;
    const marker = markers.find((item) => item.id === selectedId);
    if (changed && marker && mapRef.current) {
      mapRef.current.panTo(new window.kakao.maps.LatLng(marker.lat, marker.lng));
    }
  }, [status, markers, selectedId]);

  if (status === 'error') {
    return fallback;
  }
  return (
    <div className={styles.wrapper}>
      <div ref={containerRef} className={styles.map} />
      {status === 'loading' && (
        <div className={styles.loading}>
          <Spinner />
        </div>
      )}
    </div>
  );
}
