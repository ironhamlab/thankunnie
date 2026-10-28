import { useMemo, useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import MapArtwork from '@/pages/home/components/MapArtwork';
import Asset from '@/components/common/Asset';
import KakaoMap from '@/components/map/KakaoMap';
import { money } from '@/utils/demoData';
import Button from '@/components/common/Button';
import styles from './VacancyMap.module.css';

const PIN_POSITIONS = {
  nail: [30, 30],
  hair: [65, 24],
  lash: [42, 52],
  facial: [73, 60],
  art: [23, 70],
  clinic: [61, 80],
};
const DEFAULT_PIN_POSITION = [50, 50];
// 아래쪽 상세 패널에 핀이 가리지 않도록 범위를 맞출 때 아래 여백을 넓게 둔다
const MAP_BOUNDS_PADDING = { top: 60, right: 70, bottom: 200, left: 50 };

export default function VacancyMap({ services }) {
  const [selectedId, setSelectedId] = useState(null);
  const selected = services.find((service) => service.id === selectedId) || services[0];
  // 좌표가 있는 빈자리만 카카오맵에 표시한다
  const markers = useMemo(
    () =>
      services
        .filter((service) => service.lat != null && service.lng != null)
        .map((service) => ({
          id: service.id,
          lat: service.lat,
          lng: service.lng,
          label: money(service.price),
          ariaLabel: `${service.shop} ${money(service.price)}`,
        })),
    [services],
  );
  return (
    <section className={`b-map ${styles.root}`} aria-label="빈자리 지도">
      <KakaoMap
        markers={markers}
        selectedId={selected.id}
        onSelect={setSelectedId}
        markerClassName={`${styles.pin} ${styles.kakaoPin}`}
        boundsPadding={MAP_BOUNDS_PADDING}
        fallback={
          <ExampleMap services={services} selectedId={selected.id} onSelect={setSelectedId} />
        }
      />
      <div className={`${styles.detail} b-panel`}>
        <div className="b-flex">
          <Asset name={selected.image} className={styles.thumb} alt={selected.shop} />
          <div>
            <h2>{selected.shop}</h2>
            <p>
              {selected.location} · {selected.time}
            </p>
            <strong>{money(selected.price)}</strong>
          </div>
        </div>
        <Button
          as={Link}
          variant="primary"
          to={generatePath(ROUTES.SERVICE_DETAIL, { id: selected.id })}
        >
          선택한 빈자리 확인
        </Button>
      </div>
    </section>
  );
}

// 카카오맵 키가 없거나 SDK를 불러오지 못할 때 보여주는 예시 지도
function ExampleMap({ services, selectedId, onSelect }) {
  const [zoom, setZoom] = useState(1);
  return (
    <>
      <div className={styles.canvas} style={{ transform: `scale(${zoom})` }}>
        <MapArtwork />
        {services.map((service) => {
          const [left, top] = Object.hasOwn(PIN_POSITIONS, service.id)
            ? PIN_POSITIONS[service.id]
            : DEFAULT_PIN_POSITION;
          return (
            <button
              key={service.id}
              className={`${styles.pin} ${selectedId === service.id ? 'is-selected' : ''}`}
              style={{
                left: `${left}%`,
                top: `${top}%`,
              }}
              aria-label={`${service.shop} ${money(service.price)}`}
              aria-pressed={selectedId === service.id}
              onClick={() => onSelect(service.id)}
            >
              {money(service.price)}
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
