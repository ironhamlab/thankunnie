import { useState } from 'react';
import { generatePath, Link } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { money } from './data';

export default function HomeBeautyMap({ services }) {
  const [selectedId, setSelectedId] = useState(services[0].id);
  const [saleOnly, setSaleOnly] = useState(false);
  const selected = services.find((service) => service.id === selectedId);
  return (
    <div className="home-map-section">
      <div className="home-map-heading">
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
      <div className="home-beauty-map">
        <div className="home-map-toolbar">
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
        <div className="home-map-roads" aria-hidden="true">
          <i />
          <i />
          <i />
          <i />
        </div>
        {services
          .filter((service) => !saleOnly || service.discount > 0)
          .map((service) => (
            <button
              className={`home-map-pin home-pin-${service.id} ${selectedId === service.id ? 'is-active' : ''}`}
              aria-pressed={selectedId === service.id}
              key={service.id}
              onClick={() => setSelectedId(service.id)}
            >
              {selectedId === service.id
                ? `ϟ ${money(service.price)} (${service.discount}%↓)`
                : `${service.shop.split(' ')[0]} ${money(service.price)}`}
            </button>
          ))}
        <span className="home-map-landmark">벨루아 뷰티</span>
        <small className="home-map-disclaimer">예시 지도 · 실제 위치와 다를 수 있어요</small>
        <div className="home-map-preview">
          <span className="home-store-icon" aria-hidden="true">
            ▤
          </span>
          <div>
            <strong>{selected.shop}</strong>
            <span>오늘 {selected.time}</span>
            <p>
              {selected.location} · {selected.name}
            </p>
          </div>
          <Link className="b-button b-primary" to={generatePath(R.DETAIL, { id: selected.id })}>
            빈자리 보기
          </Link>
        </div>
      </div>
    </div>
  );
}
