import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { money } from '@/utils/demoData';
import Button from '@/components/common/Button';
import styles from './HomeBeautyMap.module.css';

export default function HomeBeautyMap({ services }) {
  const [selectedId, setSelectedId] = useState(services[0].id);
  const [saleOnly, setSaleOnly] = useState(false);
  const selected = services.find((service) => service.id === selectedId);
  return (
    <div className={styles.section}>
      <div className={styles.heading}>
        <h2>내 주변 실시간 빈자리 지도</h2>
        <span>● 강남역 부근</span>
        <button
          onClick={() => {
            setSelectedId(services[0].id);
            setSaleOnly(false);
          }}
        >
          ⌖ 지도 초기화
        </button>
      </div>
      <div className={styles.root}>
        <div className={styles.toolbar}>
          <span>⌖ 강남역 11번 출구 도보 500m 이내</span>
          <div>
            <button aria-pressed={!saleOnly} onClick={() => setSaleOnly(false)}>
              전체
            </button>
            <button aria-pressed={saleOnly} onClick={() => setSaleOnly(true)}>
              타임세일만
            </button>
          </div>
        </div>
        <div className={styles.roads} aria-hidden="true">
          <i />
          <i />
          <i />
          <i />
        </div>
        {services
          .filter((service) => !saleOnly || service.discount > 0)
          .map((service) => (
            <button
              className={`${styles.pin} home-pin-${service.id} ${selectedId === service.id ? 'is-active' : ''}`}
              aria-pressed={selectedId === service.id}
              key={service.id}
              onClick={() => setSelectedId(service.id)}
            >
              {selectedId === service.id
                ? `ϟ ${money(service.price)} (${service.discount}%↓)`
                : `${service.shop.split(' ')[0]} ${money(service.price)}`}
            </button>
          ))}
        <span className={styles.landmark}>벨루아 뷰티</span>
        <small className={styles.disclaimer}>예시 지도 · 실제 위치와 다를 수 있어요</small>
        <div className={styles.preview}>
          <span className={styles.storeIcon} aria-hidden="true">
            ▤
          </span>
          <div>
            <strong>{selected.shop}</strong>
            <span>오늘 {selected.time}</span>
            <p>
              {selected.location} · {selected.name}
            </p>
          </div>
          <Button
            as={Link}
            variant="primary"
            to={generatePath(ROUTES.SERVICE_DETAIL, { id: selected.id })}
          >
            빈자리 보기
          </Button>
        </div>
      </div>
    </div>
  );
}
