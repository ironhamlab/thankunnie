import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import MapArtwork from '@/pages/home/components/MapArtwork';
import Asset from '@/components/common/Asset';
import { money } from '@/utils/demoData';
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

export default function VacancyMap({ services }) {
  const [selectedId, setSelectedId] = useState(null);
  const [zoom, setZoom] = useState(1);
  const selected = services.find((service) => service.id === selectedId) || services[0];
  return (
    <section className={`b-map ${styles.vacancyMap}`} aria-label="빈자리 예시 지도">
      <div className={styles.mapCanvas} style={{ transform: `scale(${zoom})` }}>
        <MapArtwork />
        {services.map((service) => {
          const [left, top] = Object.hasOwn(PIN_POSITIONS, service.id)
            ? PIN_POSITIONS[service.id]
            : DEFAULT_PIN_POSITION;
          return (
            <button
              key={service.id}
              className={`${styles.mapPin} ${selected.id === service.id ? 'is-selected' : ''}`}
              style={{
                left: `${left}%`,
                top: `${top}%`,
              }}
              aria-label={`${service.shop} ${money(service.price)}`}
              aria-pressed={selected.id === service.id}
              onClick={() => setSelectedId(service.id)}
            >
              {money(service.price)}
            </button>
          );
        })}
      </div>
      <span className={styles.vacancyMapNote}>예시 지도 · 실제 위치와 다를 수 있어요</span>
      <div className={styles.mapTools}>
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
      <div className={`${styles.mapDetail} b-panel`}>
        <div className="b-flex">
          <Asset name={selected.image} className={styles.mapThumb} alt={selected.shop} />
          <div>
            <h2>{selected.shop}</h2>
            <p>
              {selected.location} · {selected.time}
            </p>
            <strong>{money(selected.price)}</strong>
          </div>
        </div>
        <Link
          className="b-button b-primary"
          to={generatePath(ROUTES.SERVICE_DETAIL, { id: selected.id })}
        >
          선택한 빈자리 확인
        </Link>
      </div>
    </section>
  );
}
