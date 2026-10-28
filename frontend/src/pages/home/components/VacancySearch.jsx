import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { todayKst, addDays } from '@/utils/scheduleDate';
import VacancyResults from './VacancyResults';
import styles from './VacancySearch.module.css';

const REGIONS = {
  '강남/서초': ['11680', '11650'],
  '홍대/연남': ['11440'],
  '성수/건대': ['11200', '11215'],
  '잠실/송파': ['11710'],
  '판교/분당': ['41135'],
};

export default function VacancySearch() {
  const [url, setUrl] = useSearchParams();
  const [date, setDate] = useState('');
  const [region, setRegion] = useState('');
  const [timeOfDay, setTime] = useState('');
  const [sort, setSort] = useState('START_ASC');
  const [keyword, setKeyword] = useState(url.get('search') ?? '');
  const [page, setPage] = useState(0);
  const today = todayKst();
  const mapView = url.get('view') === 'map';
  const saleOnly = url.get('sale') === '1';
  const params = {
    startDate: date || today,
    endDate: date || addDays(today, 6),
    regionCode: REGIONS[region],
    timeOfDay: timeOfDay || undefined,
    sort,
    keyword: keyword.trim() || undefined,
    priceTier: saleOnly ? 'IMMINENT' : undefined,
    size: 20,
  };
  const change = (setter) => (event) => {
    setter(event.target.value);
    setPage(0);
  };
  const toggle = (key, value) => {
    setUrl((previous) => {
      const next = new URLSearchParams(previous);
      next.set(key, value);
      return next;
    });
    setPage(0);
  };
  return (
    <section className={`b-container ${styles.home}`}>
      <h1>예약 가능한 빈자리 찾기</h1>
      <div className="b-panel b-row" aria-label="빈자리 검색 필터">
        <label>
          날짜{' '}
          <input
            type="date"
            value={date}
            min={today}
            max={addDays(today, 59)}
            onChange={change(setDate)}
          />
        </label>
        <label>
          지역{' '}
          <select value={region} onChange={change(setRegion)}>
            <option value="">전체 지역</option>
            {Object.keys(REGIONS).map((name) => (
              <option key={name}>{name}</option>
            ))}
          </select>
        </label>
        <label>
          시작 시간{' '}
          <select value={timeOfDay} onChange={change(setTime)}>
            <option value="">전체 시간</option>
            <option value="MORNING">오전</option>
            <option value="AFTERNOON">오후</option>
            <option value="EVENING">저녁</option>
          </select>
        </label>
        <label>
          정렬{' '}
          <select value={sort} onChange={change(setSort)}>
            <option value="START_ASC">빠른 시작순</option>
            <option value="DISCOUNT_DESC">할인율순</option>
            <option value="PRICE_ASC">낮은 가격순</option>
          </select>
        </label>
        <label>
          매장·시술 검색 <input maxLength={50} value={keyword} onChange={change(setKeyword)} />
        </label>
        <label>
          <input
            type="checkbox"
            checked={saleOnly}
            onChange={(event) => toggle('sale', event.target.checked ? '1' : '0')}
          />
          임박 특가만
        </label>
        <button aria-pressed={mapView} onClick={() => toggle('view', mapView ? 'list' : 'map')}>
          {mapView ? '목록 보기' : '지도 보기'}
        </button>
      </div>
      <VacancyResults
        sectionKey="search"
        params={params}
        mapView={mapView}
        page={page}
        onPage={setPage}
      />
    </section>
  );
}
