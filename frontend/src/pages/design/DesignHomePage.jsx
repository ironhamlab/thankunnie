import { useState } from 'react';
import { Link } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import { SERVICES } from './data';
import { useDesignStore } from './useDesignStore';
import Asset from './Asset';
import ServiceCard from './ServiceCard';

const categories = ['전체', '네일', '헤어', '속눈썹', '피부관리', '왁싱'];
const regions = ['강남/서초', '홍대/연남', '성수/건대', '잠실/송파', '판교/분당'];
const times = ['전체', '오전 (09~12시)', '오후 (12~18시)', '저녁 (18시 이후)'];
const sorts = ['임박 빈자리 우선순', '할인율순', '빠른 시작순'];
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
export default function DesignHomePage() {
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
  if (sort === '할인율순') results = [...results].sort((a, b) => b.discount - a.discount);
  if (sort === '빠른 시작순')
    results = [...results].sort((a, b) => a.day - b.day || a.time.localeCompare(b.time));
  if (mode === '검색 결과 없음') results = [];
  const reset = () => {
    setCategory('전체');
    setRegion('');
    setDay(null);
    setTime('전체');
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
          <p>당일·임박 빈자리를 특가로 겟하는 기회! 원장님 당일 스케줄 취소석을 바로 예약하세요.</p>
        </div>
        <span className="b-demo-note">
          <Asset name="8-3-7.svg" />※ 본 화면의 할인율, 예약금, 잔여 시간은{' '}
          <strong>MVP 모의 정책</strong> 예시 데이터입니다
        </span>
      </section>
      <section className="b-filters b-panel" aria-label="빈자리 검색 필터">
        <div className="b-filter-group b-date-filter">
          <span className="b-filter-label">날짜</span>
          <div className="b-options">
            {[
              '오늘 10.24 (목)',
              '내일 10.25 (금)',
              '10.26 (토)',
              '10.27 (일)',
              '10.28 (월)',
              '10.29 (화)',
              '10.30 (수)',
            ].map((label, index) => (
              <button
                key={label}
                aria-pressed={day === index}
                className={day === index || (day === null && index === 0) ? 'is-selected' : ''}
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
        <small className="b-muted">실시간 잔여 슬롯이 빠르게 마감될 수 있습니다.</small>
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
        <div className="b-card-grid">
          {results.map((s) => (
            <ServiceCard key={s.id} service={s} />
          ))}
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
        <Link to={R.EXPLORE}>지도·목록 탐색 화면 보기 →</Link>
        <small>시연 기준일: 2025.10.24 · 선택한 날짜를 다시 누르면 전체 날짜를 봅니다.</small>
      </div>
    </div>
  );
}
