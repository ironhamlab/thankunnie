import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useDesignStore } from '@/store/useDesignStore';
import { useExploreStore } from '@/store/useExploreStore';
import { REGION_CODES, SORTS, TIME_OF_DAY, upcomingDays } from '@/utils/exploreVacancy';
import { addDays, todayKst } from '@/utils/scheduleDate';
import FireIcon from '@/components/icons/FireIcon';
import InfoIcon from '@/components/icons/InfoIcon';
import ServiceCard from '@/pages/home/components/ServiceCard';
import VacancyMap from '@/pages/home/components/VacancyMap';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import './VacancySearch.css';
import styles from './VacancySearch.module.css';

const ALL = '전체';
const regions = Object.keys(REGION_CODES);
const times = [ALL, ...Object.keys(TIME_OF_DAY)];
const sorts = Object.keys(SORTS);
const DATE_CHIPS = 7;
// 예약은 오늘 포함 60일까지 가능하다
const MAX_DAYS = 60;
// 헤더 검색어는 글자마다 바뀌므로 입력이 멈춘 뒤 조회한다
const KEYWORD_DELAY_MS = 300;

function Filter({ label, options, value, onChange, segmented = false }) {
  return (
    <div className={`${styles.filterGroup} ` + (segmented ? styles.segmented : '')}>
      <span className={styles.filterLabel}>{label}</span>
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
  const shopId = params.get('shop');
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
  const vacancies = useExploreStore((s) => s.vacancies);
  const totalElements = useExploreStore((s) => s.totalElements);
  const hasNext = useExploreStore((s) => s.hasNext);
  const status = useExploreStore((s) => s.status);
  const error = useExploreStore((s) => s.error);
  const categories = useExploreStore((s) => s.categories);
  const searchVacancies = useExploreStore((s) => s.searchVacancies);
  const loadMoreVacancies = useExploreStore((s) => s.loadMoreVacancies);
  const loadCategories = useExploreStore((s) => s.loadCategories);
  const [category, setCategory] = useState(ALL);
  const [region, setRegion] = useState('');
  const [day, setDay] = useState(null);
  const [time, setTime] = useState(ALL);
  const [sort, setSort] = useState(sorts[0]);
  const [keyword, setKeyword] = useState(search);
  const today = todayKst();
  const dateChips = useMemo(() => upcomingDays(DATE_CHIPS, today), [today]);

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  useEffect(() => {
    const timer = window.setTimeout(() => setKeyword(search.trim()), KEYWORD_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [search]);

  // 분야 목록이 늦게 도착해도 같은 조건으로 다시 조회하지 않도록 ID 만 꺼내 둔다
  const categoryId = categories.find((item) => item.name === category)?.categoryId;
  const query = useMemo(() => {
    // 타임세일은 오늘 시작하는 임박·초임박 특가만 본다
    const date = saleOnly ? today : day;
    return {
      startDate: date ?? undefined,
      endDate: date ?? undefined,
      regionCode: region ? REGION_CODES[region] : undefined,
      categoryId,
      shopId: shopId ?? undefined,
      timeOfDay: TIME_OF_DAY[time],
      priceTier: saleOnly ? 'IMMINENT' : undefined,
      keyword: keyword || undefined,
      sort: SORTS[sort],
    };
  }, [categoryId, saleOnly, today, day, region, shopId, time, keyword, sort]);

  useEffect(() => {
    searchVacancies(query);
  }, [query, searchVacancies]);

  const reset = () => {
    setCategory(ALL);
    setRegion('');
    setDay(null);
    setTime(ALL);
    setSort(sorts[0]);
    setParams({ view: mapView ? 'map' : 'list' }, { replace: true });
    resetSearch('');
  };
  return (
    <div className={`b-container ${styles.home}`}>
      <section className="b-hero">
        <div>
          <div className="b-eyebrow">
            <FireIcon size={16} />
            REAL-TIME BEAUTY FLASH SALE
          </div>
          <h1>
            지금 예약 가능한 <em>뷰티 빈자리</em>를 찾아보세요
          </h1>
          <p>원하는 날짜와 지역의 빈자리를 찾고, 오늘의 할인 자리는 타임세일로 모아보세요.</p>
        </div>
        <span className={styles.demoNote}>
          <InfoIcon size={20} className={styles.noteIcon} />
          가격은 조회 시점 기준이며, 예약할 때 <strong>다시 확인</strong>해 드려요
        </span>
      </section>
      <section className={`${styles.filters} b-panel`} aria-label="빈자리 검색 필터">
        <Filter
          label="예약 종류"
          options={[ALL, '타임세일']}
          value={saleOnly ? '타임세일' : ALL}
          onChange={(value) => updateOption('sale', value === '타임세일' ? '1' : '')}
        />
        <p className={styles.description}>
          {saleOnly
            ? '오늘 시작하는 임박·초임박 특가 빈자리만 보여드려요.'
            : '정상가와 할인가를 포함한 모든 날짜의 빈자리를 보여드려요.'}
        </p>
        {shopId && (
          <p className={styles.description}>
            지도에서 고른 매장의 빈자리만 보여드려요.{' '}
            <button
              type="button"
              className={styles.clearShop}
              onClick={() => updateOption('shop', '')}
            >
              전체 매장 보기
            </button>
          </p>
        )}
        <div className={`${styles.filterGroup} ${styles.dateFilter}`}>
          <span className={styles.filterLabel}>날짜</span>
          <div className="b-options">
            <button
              aria-pressed={day === null}
              className={day === null ? 'is-selected' : ''}
              onClick={() => setDay(null)}
            >
              전체 날짜
            </button>
            {dateChips.map(({ date, label }, index) => (
              <button
                key={date}
                aria-pressed={day === date}
                className={day === date ? 'is-selected' : ''}
                onClick={() => setDay(day === date ? null : date)}
              >
                {label}
                {index === 0 && ' ●'}
              </button>
            ))}
            <label className={styles.moreDate}>
              + 다른 날짜
              <input
                type="date"
                aria-label="추가 날짜 선택"
                min={today}
                max={addDays(today, MAX_DAYS - 1)}
                onChange={(event) => setDay(event.target.value || null)}
              />
            </label>
          </div>
        </div>
        <div className={`b-row ${styles.filterLine}`}>
          <Filter
            label="시술 분야"
            options={[ALL, ...categories.map((item) => item.name)]}
            value={category}
            onChange={setCategory}
          />
          <Filter label="주요 지역" options={regions} value={region} onChange={setRegion} />
        </div>
        <div className={`b-row ${styles.filterLine}`}>
          <Filter label="시작 시간" options={times} value={time} onChange={setTime} segmented />
          <Filter label="정렬" options={sorts} value={sort} onChange={setSort} segmented />
        </div>
      </section>
      <div className={`b-row ${styles.resultsHeading}`}>
        <h2>
          예약 가능한 빈자리 <Badge>{totalElements}건</Badge>
        </h2>
        <Filter
          label="보기 방식"
          options={['목록', '지도']}
          value={mapView ? '지도' : '목록'}
          onChange={(value) => updateOption('view', value === '지도' ? 'map' : '')}
          segmented
        />
      </div>
      {status === 'loading' || status === 'idle' ? (
        <div className={styles.cardGrid} aria-busy="true" aria-label="빈자리 불러오는 중">
          {[1, 2, 3].map((id) => (
            <div key={id} className="b-skeleton" />
          ))}
        </div>
      ) : status === 'failed' ? (
        <EmptyState className="b-panel" role="alert">
          <h2>빈자리를 불러오지 못했어요</h2>
          <p>{error?.message ?? '잠시 후 다시 시도해 주세요.'}</p>
          <Button variant="primary" onClick={() => searchVacancies(query)}>
            다시 시도
          </Button>
        </EmptyState>
      ) : vacancies.length ? (
        <>
          <div className={mapView ? 'b-vacancy-map-layout' : ''}>
            <div className={styles.cardGrid}>
              {vacancies.map((s) => (
                <ServiceCard key={s.id} service={s} />
              ))}
            </div>
            {mapView && <VacancyMap services={vacancies} />}
          </div>
          {error && (
            <p className="b-muted" role="alert">
              {error.message}
            </p>
          )}
          {hasNext && (
            <div className="b-row">
              <Button
                variant="soft"
                disabled={status === 'loadingMore'}
                onClick={loadMoreVacancies}
              >
                {status === 'loadingMore' ? '불러오는 중…' : '빈자리 더보기'}
              </Button>
            </div>
          )}
        </>
      ) : (
        <EmptyState className="b-panel">
          <h2>조건에 맞는 빈자리가 없어요</h2>
          <p>다른 날짜나 지역을 선택해 보세요.</p>
          <Button variant="primary" onClick={reset}>
            필터 초기화
          </Button>
        </EmptyState>
      )}
      <div className="b-row b-help">
        <small>목록과 지도는 같은 검색 결과를 표시합니다.</small>
      </div>
    </div>
  );
}
