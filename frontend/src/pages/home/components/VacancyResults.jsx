import { useEffect } from 'react';
import { useExploreStore } from '@/store/useExploreStore';
import VacancyMap from './VacancyMap';
import Button from '@/components/common/Button';
import styles from './VacancySearch.module.css';

const dateFormat = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** 서버의 가격과 좌표를 홈·검색·지도에서 함께 사용한다. */
export default function VacancyResults({ sectionKey, params, mapView = false, page = 0, onPage }) {
  const section = useExploreStore((state) => state.sections[sectionKey]);
  const load = useExploreStore((state) => state.load);
  const query = JSON.stringify({ ...params, page });
  useEffect(() => {
    load(sectionKey, JSON.parse(query));
  }, [load, sectionKey, query]);
  const items = section?.data?.content;
  if (!section || section.status === 'loading')
    return <p role="status">빈자리를 불러오는 중입니다.</p>;
  if (section.status === 'failed')
    return (
      <div role="alert">
        <p>{section.error}</p>
        <Button onClick={() => load(sectionKey, JSON.parse(query))}>다시 시도</Button>
      </div>
    );
  if (!items?.length)
    return (
      <div>
        <p>조건에 맞는 빈자리가 없습니다.</p>
        {onPage && page > 0 && <Button onClick={() => onPage(page - 1)}>이전 페이지</Button>}
      </div>
    );
  return (
    <>
      {mapView && <VacancyMap items={items} />}
      <p>예약 가능한 빈자리 {section.data.totalElements}건</p>
      <div className={styles.cardGrid}>
        {items.map((item) => (
          <article key={item.vacancyId} className="b-panel">
            <h3>{item.shopName}</h3>
            <p>{item.treatmentName}</p>
            <p>{item.address}</p>
            <p>시작 {dateFormat.format(new Date(item.startsAt))}</p>
            <p>예약 마감 {dateFormat.format(new Date(item.bookingClosesAt))}</p>
            <strong>{item.salePrice.toLocaleString('ko-KR')}원</strong>
          </article>
        ))}
      </div>
      {onPage && (
        <div className="b-row">
          <Button disabled={page === 0} onClick={() => onPage(page - 1)}>
            이전
          </Button>
          <span>{page + 1}페이지</span>
          <Button disabled={!section.data.hasNext} onClick={() => onPage(page + 1)}>
            다음
          </Button>
        </div>
      )}
    </>
  );
}
