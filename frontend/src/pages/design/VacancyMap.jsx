import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import MapArtwork from './MapArtwork';
import Asset from './Asset';
import { money, SERVICES } from './data';

const positions = [
  [30, 30],
  [65, 24],
  [42, 52],
  [73, 60],
  [23, 70],
  [61, 80],
];

export default function VacancyMap({ services }) {
  const [selectedId, setSelectedId] = useState(null);
  const [zoom, setZoom] = useState(1);
  const selected = services.find((service) => service.id === selectedId) || services[0];
  return (
    <section className="b-map b-vacancy-map" aria-label="빈자리 예시 지도">
      <div className="b-map-canvas" style={{ transform: `scale(${zoom})` }}>
        <MapArtwork />
        {services.map((service) => (
          <button
            key={service.id}
            className={`b-map-pin ${selected.id === service.id ? 'is-selected' : ''}`}
            style={{
              left: `${positions[SERVICES.findIndex((item) => item.id === service.id)][0]}%`,
              top: `${positions[SERVICES.findIndex((item) => item.id === service.id)][1]}%`,
            }}
            aria-label={`${service.shop} ${money(service.price)}`}
            aria-pressed={selected.id === service.id}
            onClick={() => setSelectedId(service.id)}
          >
            {money(service.price)}
          </button>
        ))}
      </div>
      <span className="b-vacancy-map-note">예시 지도 · 실제 위치와 다를 수 있어요</span>
      <div className="b-map-tools">
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
      <div className="b-map-detail b-panel">
        <div className="b-flex">
          <Asset name={selected.image} className="b-map-thumb" alt={selected.shop} />
          <div>
            <h2>{selected.shop}</h2>
            <p>
              {selected.location} · {selected.time}
            </p>
            <strong>{money(selected.price)}</strong>
          </div>
        </div>
        <Link className="b-button b-primary" to={generatePath(R.DETAIL, { id: selected.id })}>
          선택한 빈자리 확인
        </Link>
      </div>
    </section>
  );
}
