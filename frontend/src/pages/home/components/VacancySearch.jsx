import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SERVICES } from '@/utils/demoData';
import { useDesignStore } from '@/store/useDesignStore';
import Asset from '@/components/common/Asset';
import ServiceCard from '@/pages/home/components/ServiceCard';
import VacancyMap from '@/pages/home/components/VacancyMap';
import './VacancySearch.css';

const categories = ['전체', '네일', '헤어', '속눈썹', '피부관리', '왁싱'];
const regions = ['강남/서초', '홍대/연남', '성수/건대', '잠실/송파', '판교/분당'];
const times = ['전체', '오전 (09~12시)', '오후 (12~18시)', '저녁 (18시 이후)'];
const sorts = ['임박 빈자리 우선순', '할인율순', '빠른 시작순'];
const URGENCY_RANK = { '초임박 특가': 0, '임박 특가': 1, '일반 빈자리': 2 };
function Filter({ label, options, value, onChange, segmented = false }) {
  return (
    <div className={'b-filter-group ' + (segmented ? 'b-segmented' : '')}>
      <span className="b-filter-label">{label}</span>
      <div className="b-options">
        {options.map((option) => (
          <button
            key={option}
            aria-pressed={value === option}
            className={value === option ? 'is-selected' : ''}
            onClick={() => onChange(value === option && label === '주요 지역' ? '' : option)}
          >
            {option}
          </button>
        ))}
      </div>
    </div>
  );
}
export default function VacancySearch() {
  const [params, setParams] = useSearchParams();
  const saleOnly = params.get('sale') === '1';
  const mapView = params.get('view') === 'map';
  const updateOption = (key, value) => {
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        if (value) next.set(key, value);
        else {
          next.delete(key);
          if (next.size === 0) next.set('view', 'list');
        }
        return next;
      },
      { replace: true },
    );
  };
  const search = useDesignStore((s) => s.search);
  const resetSearch = useDesignStore((s) => s.setSearch);
  const [category, setCategory] = useState('전체');
  const [region, setRegion] = useState('');
  const [day, setDay] = useState(null);
  const [time, setTime] = useState('전체');
  const [sort, setSort] = useState(sorts[0]);
  const [mode, setMode] = useState('정상 결과 (6건)');
  let results = SERVICES.filter(
    (s) =>
      (!saleOnly || (s.discount > 0 && s.day === 0)) &&
      (category === '전체' || s.category === category) &&
      (!region || s.region === region) &&
      (day === null || s.day === day) &&
      (!search || (s.name + s.shop + s.location + s.category).includes(search)) &&
      (time === '전체' ||
        (time.startsWith('오전') && Number(s.time.slice(0, 2)) < 12) ||
        (time.startsWith('오후') &&
          Number(s.time.slice(0, 2)) >= 12 &&
          Number(s.time.slice(0, 2)) < 18) ||
        (time.startsWith('저녁') && Number(s.time.slice(0, 2)) >= 18)),
  );
  if (sort === sorts[0])
    results = [...results].sort(
      (a, b) =>
        (URGENCY_RANK[a.urgency] ?? 3) - (URGENCY_RANK[b.urgency] ?? 3) ||
        a.day - b.day ||
        a.time.localeCompare(b.time),
    );
  if (sort === '할인율순') results = [...results].sort((a, b) => b.discount - a.discount);
  if (sort === '빠른 시작순')
    results = [...results].sort((a, b) => a.day - b.day || a.time.localeCompare(b.time));
  if (mode === '검색 결과 없음') results = [];
  const reset = () => {
    setCategory('전체');
    setRegion('');
    setDay(null);
    setTime('전체');
    setSort(sorts[0]);
    updateOption('sale', '');
    setMode('정상 결과 (6건)');
    resetSearch('');
  };
  return (
    <div className="b-container b-home">
      <section className="b-hero">
        <div>
          <div className="b-eyebrow">
            <Asset name="8-3-6.svg" />
            REAL-TIME BEAUTY FLASH SALE
          </div>
          <h1>
            지금 예약 가능한 <em>뷰티 빈자리</em>를 찾아보세요
          </h1>
          <p>원하는 날짜와 지역의 빈자리를 찾고, 오늘의 할인 자리는 타임세일로 모아보세요.</p>
        </div>
        <span className="b-demo-note">
          <Asset name="8-3-7.svg" />※ 본 화면의 할인율, 예약금, 잔여 시간은{' '}
          <strong>MVP 모의 정책</strong> 예시 데이터입니다
        </span>
      </section>
      <section className="b-filters b-panel" aria-label="빈자리 검색 필터">
        <Filter
          label="예약 종류"
          options={['전체', '타임세일']}
          value={saleOnly ? '타임세일' : '전체'}
          onChange={(value) => updateOption('sale', value === '타임세일' ? '1' : '')}
        />
        <p className="b-vacancy-description">
          {saleOnly
            ? '오늘 시작하는 할인 빈자리만 보여드려요.'
            : '정상가와 할인가를 포함한 모든 날짜의 빈자리를 보여드려요.'}
        </p>
        <div className="b-filter-group b-date-filter">
          <span className="b-filter-label">날짜</span>
          <div className="b-options">
            <button
              aria-pressed={day === null}
              className={day === null ? 'is-selected' : ''}
              onClick={() => setDay(null)}
            >
              전체 날짜
            </button>
            {[
              '오늘 10.24 (금)',
              '내일 10.25 (토)',
              '10.26 (일)',
              '10.27 (월)',
              '10.28 (화)',
              '10.29 (수)',
              '10.30 (목)',
            ].map((label, index) => (
              <button
                key={label}
                aria-pressed={day === index}
                className={day === index ? 'is-selected' : ''}
                onClick={() => setDay(day === index ? null : index)}
              >
                {label}
                {index === 0 && ' ●'}
              </button>
            ))}
            <label className="b-more-date">
              + 31일 보기
              <input
                type="date"
                aria-label="추가 날짜 선택"
                min="2025-10-24"
                max="2025-11-23"
                onChange={(event) => {
                  if (event.target.value)
                    setDay(
                      Math.round(
                        (new Date(event.target.value) - new Date('2025-10-24')) / 86400000,
                      ),
                    );
                }}
              />
            </label>
          </div>
        </div>
        <div className="b-row b-filter-line">
          <Filter label="시술 분야" options={categories} value={category} onChange={setCategory} />
          <Filter label="주요 지역" options={regions} value={region} onChange={setRegion} />
        </div>
        <div className="b-row b-filter-line">
          <Filter label="시작 시간" options={times} value={time} onChange={setTime} segmented />
          <Filter label="정렬" options={sorts} value={sort} onChange={setSort} segmented />
        </div>
      </section>
      <div className="b-row b-results-heading">
        <h2>
          예약 가능한 빈자리 <span className="b-badge">{results.length}건</span>
        </h2>
        <Filter
          label="보기 방식"
          options={['목록', '지도']}
          value={mapView ? '지도' : '목록'}
          onChange={(value) => updateOption('view', value === '지도' ? 'map' : '')}
          segmented
        />
      </div>
      {mode === '로딩 상태' ? (
        <div className="b-card-grid" aria-busy="true" aria-label="빈자리 불러오는 중">
          {[1, 2, 3].map((id) => (
            <div key={id} className="b-skeleton" />
          ))}
        </div>
      ) : mode === '조회 오류' ? (
        <div className="b-empty b-panel" role="alert">
          <h2>빈자리를 불러오지 못했어요</h2>
          <p>잠시 후 다시 시도해 주세요.</p>
          <button className="b-button b-primary" onClick={() => setMode('정상 결과 (6건)')}>
            다시 시도
          </button>
        </div>
      ) : results.length ? (
        <div className={mapView ? 'b-vacancy-map-layout' : ''}>
          <div className="b-card-grid">
            {results.map((s) => (
              <ServiceCard key={s.id} service={s} />
            ))}
          </div>
          {mapView && <VacancyMap services={results} />}
        </div>
      ) : (
        <div className="b-empty b-panel">
          <h2>조건에 맞는 빈자리가 없어요</h2>
          <p>다른 날짜나 지역을 선택해 보세요.</p>
          <button className="b-button b-primary" onClick={reset}>
            필터 초기화
          </button>
        </div>
      )}
      <div className="b-preview b-row">
        <div className="b-flex">
          <Asset name="8-3-12.svg" />
          <strong>상태별 UI 미리보기 모드:</strong>
          <span className="b-muted">개발 및 검증을 위한 화면 상태 토글</span>
        </div>
        <div className="b-options">
          {['정상 결과 (6건)', '검색 결과 없음', '로딩 상태', '조회 오류'].map((item) => (
            <button
              key={item}
              aria-pressed={mode === item}
              className={mode === item ? 'is-selected' : ''}
              onClick={() => setMode(item)}
            >
              {item}
            </button>
          ))}
        </div>
      </div>
      <div className="b-row b-help">
        <small>시연 기준일: 2025.10.24 · 목록과 지도는 같은 검색 결과를 표시합니다.</small>
      </div>
    </div>
  );
}
