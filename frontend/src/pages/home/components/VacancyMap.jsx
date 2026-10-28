import { useMemo, useState } from 'react';
import KakaoMap from '@/components/map/KakaoMap';
import { vacancyMarkers } from '@/utils/vacancyResponse';
import styles from './VacancyMap.module.css';

export default function VacancyMap({ items }) {
  const [selectedId, setSelectedId] = useState(null);
  const markers = useMemo(() => vacancyMarkers(items), [items]);
  const selected = items.find((item) => item.vacancyId === selectedId);
  return (
    <section className={`b-map ${styles.root}`} aria-label="빈자리 지도">
      <KakaoMap
        markers={markers}
        selectedId={selectedId}
        onSelect={setSelectedId}
        markerClassName={`${styles.pin} ${styles.kakaoPin}`}
        fallback={<p>지도를 사용할 수 없어 목록으로 안내합니다.</p>}
      />
      {selected && (
        <div className={`${styles.detail} b-panel`}>
          <strong>{selected.shopName}</strong>
          <p>
            {selected.treatmentName} · {selected.address}
          </p>
          <strong>{selected.salePrice.toLocaleString('ko-KR')}원</strong>
        </div>
      )}
    </section>
  );
}
