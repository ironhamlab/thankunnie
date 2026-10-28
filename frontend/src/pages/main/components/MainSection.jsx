import { useMemo, useState } from 'react';
import Alert from '@/components/common/Alert';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import Panel from '@/components/common/Panel';
import KakaoMap from '@/components/map/KakaoMap';
import { MAIN_PRESETS } from '@/constants/mainPresets';
import { hasItemCoordinates } from '@/utils/mainItemResponse';
import styles from '../MainPage.module.css';

const wonFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** C 목록의 ID를 시연용 상세 화면의 문자열 ID로 변환하지 않는다. */
export default function MainSection({ preset, useMainStore }) {
  const section = useMainStore((state) => state.sections[preset]);
  const fetchSection = useMainStore((state) => state.fetchSection);
  const [selectedId, setSelectedId] = useState(null);
  const definition = MAIN_PRESETS[preset];
  const markers = useMemo(
    () =>
      (section.data?.content ?? []).filter(hasItemCoordinates).map((item) => ({
        id: item.id,
        lat: item.latitude,
        lng: item.longitude,
        label: wonFormatter.format(item.price),
        ariaLabel: `${item.shopName} ${wonFormatter.format(item.price)}`,
      })),
    [section.data],
  );

  return (
    <section
      className={styles.section}
      aria-labelledby={`main-${preset}`}
      aria-busy={section.status === 'loading'}
    >
      <h2 id={`main-${preset}`}>{definition.title}</h2>
      {(section.status === 'idle' || section.status === 'loading') && (
        <EmptyState role="status">빈자리를 불러오는 중입니다.</EmptyState>
      )}
      {section.status === 'failed' && (
        <Alert tone="error" role="alert">
          <p>{section.error.message}</p>
          <Button onClick={() => fetchSection(preset)}>다시 시도</Button>
        </Alert>
      )}
      {section.status === 'succeeded' && section.data.content.length === 0 && (
        <EmptyState>{definition.emptyMessage}</EmptyState>
      )}
      {section.status === 'succeeded' && section.data.content.length > 0 && (
        <>
          <ul className={styles.list}>
            {section.data.content.map((item) => (
              <li key={item.id}>
                <Panel as="article" padded>
                  <p>{item.shopName}</p>
                  <h3>{item.title}</h3>
                  <p>
                    시작{' '}
                    <time dateTime={item.startsAt}>
                      {dateFormatter.format(new Date(item.startsAt))}
                    </time>
                  </p>
                  {item.applicationClosesAt && (
                    <p>
                      신청 마감{' '}
                      <time dateTime={item.applicationClosesAt}>
                        {dateFormatter.format(new Date(item.applicationClosesAt))}
                      </time>
                    </p>
                  )}
                  <strong className={styles.price}>{wonFormatter.format(item.price)}</strong>
                  {!hasItemCoordinates(item) && <p>위치 정보 준비 중</p>}
                </Panel>
              </li>
            ))}
          </ul>
          {markers.length > 0 && (
            <div
              className={styles.map}
              role="region"
              aria-label={`${definition.title} 빈자리 지도`}
            >
              <KakaoMap
                markers={markers}
                selectedId={selectedId}
                onSelect={setSelectedId}
                markerClassName={styles.marker}
                fallback={
                  <EmptyState>
                    지도를 불러올 수 없습니다. 위 목록에서 매장과 가격을 확인해 주세요.
                  </EmptyState>
                }
              />
            </div>
          )}
        </>
      )}
    </section>
  );
}
