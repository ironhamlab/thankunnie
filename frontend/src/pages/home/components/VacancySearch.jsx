import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useDesignStore } from '@/store/useDesignStore';
import { useExploreStore } from '@/store/useExploreStore';
import { TIME_OF_DAY, dayLabel } from '@/utils/exploreVacancy';
import { addDays, todayKst } from '@/utils/scheduleDate';
import FireIcon from '@/components/icons/FireIcon';
import InfoIcon from '@/components/icons/InfoIcon';
import ShopRow from '@/pages/home/components/ShopRow';
import ShopLocationMap from '@/pages/home/components/ShopLocationMap';
import DateCalendar from '@/pages/home/components/DateCalendar';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import './VacancySearch.css';
import styles from './VacancySearch.module.css';

const ALL = '전체';
const times = [ALL, ...Object.keys(TIME_OF_DAY)];
// 매장 정렬. 같은 값이면 매장 ID 순으로 둔다
const SHOP_SORTS = {
  '빠른 시작순': (a, b) => a.earliestStartsAt.localeCompare(b.earliestStartsAt),
  할인율순: (a, b) => b.maxDiscountRate - a.maxDiscountRate,
  '낮은 가격순': (a, b) => a.minSalePrice - b.minSalePrice,
};
const sorts = Object.keys(SHOP_SORTS);
// 처음에 보여줄 매장 수. 더보기를 누를 때마다 이만큼 늘린다
const PAGE_SIZE = 12;
// 예약은 오늘 포함 60일까지 가능하다
const MAX_DAYS = 60;
// 헤더 검색어는 글자마다 바뀌므로 입력이 멈춘 뒤 조회한다
const KEYWORD_DELAY_MS = 300;
// 고른 날짜에 빈자리가 없을 때 가장 가까운 날짜를 찾아볼 기간(일)
const NEXT_SEARCH_DAYS = 14;
const MODEL = 'MODEL';

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
            onClick={() => onChange(option)}
          >
            {option}
          </button>
        ))}
      </div>
    </div>
  );
}

/**
 * 빈자리 검색 화면 (/?view=list). 분야·날짜·시간대 조건에 맞는 매장을 한 줄씩 보여주고(C-02),
 * 매장을 펼치면 시술별 시작 시각(C-01)을 보여준다. 옆 지도에는 같은 매장 위치를 띄운다.
 * 분야·날짜·펼친 매장·타임세일·모델 모집 여부는 주소에 남겨 새로고침이나 홈에서 들어와도 같은 화면을 보여준다.
 */
export default function VacancySearch() {
  const [params, setParams] = useSearchParams();
  const today = todayKst();
  const lastDay = addDays(today, MAX_DAYS - 1);
  const saleOnly = params.get('sale') === '1';
  const modelOnly = params.get('type') === MODEL;
  const expandedId = params.get('shop');
  const categoryId = params.get('category');
  const dateParam = params.get('date');
  // 타임세일은 오늘 시작하는 빈자리만 보고, 그 밖에는 고른 날짜(기본 오늘)를 본다
  const date = !saleOnly && dateParam >= today && dateParam <= lastDay ? dateParam : today;
  const updateOption = (key, value) => {
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        if (value) next.set(key, value);
        else next.delete(key);
        if (next.size === 0) next.set('view', 'list');
        return next;
      },
      { replace: true },
    );
  };
  const search = useDesignStore((s) => s.search);
  const resetSearch = useDesignStore((s) => s.setSearch);
  const shops = useExploreStore((s) => s.searchShops);
  const status = useExploreStore((s) => s.searchShopsStatus);
  const shopVacancies = useExploreStore((s) => s.shopVacancies);
  const categories = useExploreStore((s) => s.categories);
  const loadSearchShops = useExploreStore((s) => s.loadSearchShops);
  const loadShopVacancies = useExploreStore((s) => s.loadShopVacancies);
  const loadCategories = useExploreStore((s) => s.loadCategories);
  const nextVacancyDate = useExploreStore((s) => s.nextVacancyDate);
  const findNextVacancyDate = useExploreStore((s) => s.findNextVacancyDate);
  const [time, setTime] = useState(ALL);
  const [sort, setSort] = useState(sorts[0]);
  const [keyword, setKeyword] = useState(search);
  const [paging, setPaging] = useState({ key: '', count: PAGE_SIZE });
  const categoryName =
    categories.find((item) => String(item.categoryId) === categoryId)?.name ?? ALL;

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  useEffect(() => {
    const timer = window.setTimeout(() => setKeyword(search.trim()), KEYWORD_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [search]);

  // 매장 목록·지도·펼친 매장의 빈자리가 같이 쓰는 조건
  const filter = useMemo(
    () => ({
      startDate: date,
      endDate: date,
      categoryId: categoryId ?? undefined,
      timeOfDay: TIME_OF_DAY[time],
      priceTier: saleOnly ? 'IMMINENT' : undefined,
      treatmentType: modelOnly ? MODEL : undefined,
      keyword: keyword || undefined,
    }),
    [date, categoryId, time, saleOnly, modelOnly, keyword],
  );
  const filterKey = JSON.stringify(filter);

  useEffect(() => {
    loadSearchShops(filter);
  }, [filter, loadSearchShops]);

  // 하루씩 보므로 빈 날짜를 자주 만난다. 같은 조건으로 다음 날부터 찾아 가장 가까운 날짜를 안내한다
  // 타임세일은 오늘만 보는 화면이라 다른 날짜를 안내하지 않는다
  const noVacancy = status === 'succeeded' && shops.length === 0;
  useEffect(() => {
    if (!noVacancy || saleOnly || date >= lastDay) return;
    const end = addDays(date, NEXT_SEARCH_DAYS);
    findNextVacancyDate({
      ...filter,
      startDate: addDays(date, 1),
      endDate: end > lastDay ? lastDay : end,
    });
  }, [noVacancy, saleOnly, date, lastDay, filter, findNextVacancyDate]);

  const detail = expandedId ? shopVacancies[expandedId] : undefined;
  useEffect(() => {
    if (expandedId && !detail && status === 'succeeded') loadShopVacancies(expandedId, filter);
  }, [expandedId, detail, status, filter, loadShopVacancies]);

  // 지도나 홈에서 고른 매장이 화면 밖이면 보이도록 옮긴다
  useEffect(() => {
    if (expandedId && status === 'succeeded') {
      document.getElementById(`shop-row-${expandedId}`)?.scrollIntoView({ block: 'nearest' });
    }
  }, [expandedId, status]);

  const sortedShops = useMemo(
    () => [...shops].sort((a, b) => SHOP_SORTS[sort](a, b) || a.shopId - b.shopId),
    [shops, sort],
  );
  // 조건이 바뀌면 처음 개수로 돌아간다. 펼친 매장은 뒤쪽에 있어도 보이게 한다
  const pageCount = paging.key === filterKey ? paging.count : PAGE_SIZE;
  const expandedIndex = sortedShops.findIndex((shop) => String(shop.shopId) === expandedId);
  const visibleShops = sortedShops.slice(0, Math.max(pageCount, expandedIndex + 1));

  const toggleShop = (shopId) =>
    updateOption('shop', String(shopId) === expandedId ? '' : String(shopId));
  const chooseCategory = (name) => {
    const id = categories.find((item) => item.name === name)?.categoryId;
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        if (id) next.set('category', id);
        else next.delete('category');
        // 분야를 바꾸면 펼쳐 둔 매장은 목록에 없을 수 있다
        next.delete('shop');
        next.set('view', 'list');
        return next;
      },
      { replace: true },
    );
  };
  const reset = () => {
    setTime(ALL);
    setSort(sorts[0]);
    setParams(modelOnly ? { view: 'list', type: MODEL } : { view: 'list' }, { replace: true });
    resetSearch('');
  };
  return (
    <div className={`b-container ${styles.home}`}>
      <section className="b-hero">
        <div>
          <div className="b-eyebrow">
            <FireIcon size={16} />
            {modelOnly ? 'MODEL RECRUITING' : 'REAL-TIME BEAUTY FLASH SALE'}
          </div>
          {modelOnly ? (
            <h1>
              <em>시술 모델</em>을 모집하는 빈자리
            </h1>
          ) : (
            <h1>
              지금 예약 가능한 <em>뷰티 빈자리</em>를 찾아보세요
            </h1>
          )}
          <p>
            {modelOnly
              ? '포트폴리오 촬영·실습을 함께할 모델을 찾는 매장의 빈자리예요.'
              : '분야와 날짜를 고르면 빈자리가 있는 매장과 위치를 함께 보여드려요.'}
          </p>
        </div>
        <span className={styles.demoNote}>
          <InfoIcon size={20} className={styles.noteIcon} />
          가격은 조회 시점 기준이며, 예약할 때 <strong>다시 확인</strong>해 드려요
        </span>
      </section>
      <section className={`${styles.filters} b-panel`} aria-label="빈자리 검색 필터">
        <div className={styles.categoryRow} role="group" aria-label="시술 분야">
          {[ALL, ...categories.map((item) => item.name)].map((name) => (
            <button
              key={name}
              type="button"
              aria-pressed={categoryName === name}
              onClick={() => chooseCategory(name)}
            >
              {name}
            </button>
          ))}
        </div>
        <div className={styles.dateRow}>
          <DateCalendar
            value={date}
            min={today}
            max={lastDay}
            disabled={saleOnly}
            onChange={(value) => updateOption('date', value === today ? '' : value)}
          />
          <Filter
            label="예약 종류"
            options={[ALL, '타임세일']}
            value={saleOnly ? '타임세일' : ALL}
            onChange={(value) => updateOption('sale', value === '타임세일' ? '1' : '')}
            segmented
          />
        </div>
        {saleOnly && (
          <p className={styles.description}>오늘 시작하는 임박·초임박 특가 빈자리만 보여드려요.</p>
        )}
        <div className={`b-row ${styles.filterLine}`}>
          <Filter label="시작 시간" options={times} value={time} onChange={setTime} segmented />
          <Filter label="정렬" options={sorts} value={sort} onChange={setSort} segmented />
        </div>
      </section>
      <div className={`b-row ${styles.resultsHeading}`}>
        <h2>
          {categoryName === ALL ? '빈자리 있는 매장' : `${categoryName} 빈자리 있는 매장`}{' '}
          <Badge>{shops.length}곳</Badge>
        </h2>
      </div>
      <div className={styles.layout}>
        <div className={styles.list}>
          {status === 'loading' || status === 'idle' ? (
            <div className={styles.rows} aria-busy="true" aria-label="매장 불러오는 중">
              {[1, 2, 3].map((id) => (
                <div key={id} className={`b-skeleton ${styles.rowSkeleton}`} />
              ))}
            </div>
          ) : status === 'failed' ? (
            <EmptyState className="b-panel" role="alert">
              <h2>빈자리를 불러오지 못했어요</h2>
              <p>잠시 후 다시 시도해 주세요.</p>
              <Button variant="primary" onClick={() => loadSearchShops(filter)}>
                다시 시도
              </Button>
            </EmptyState>
          ) : shops.length ? (
            <>
              <div className={styles.rows}>
                {visibleShops.map((shop) => (
                  <div key={shop.shopId} id={`shop-row-${shop.shopId}`}>
                    <ShopRow
                      shop={shop}
                      expanded={String(shop.shopId) === expandedId}
                      detail={String(shop.shopId) === expandedId ? detail : undefined}
                      onToggle={() => toggleShop(shop.shopId)}
                      onRetry={() => loadShopVacancies(String(shop.shopId), filter)}
                    />
                  </div>
                ))}
              </div>
              {visibleShops.length < sortedShops.length && (
                <div className={styles.more}>
                  <Button
                    variant="soft"
                    onClick={() => setPaging({ key: filterKey, count: pageCount + PAGE_SIZE })}
                  >
                    매장 더보기 ({sortedShops.length - visibleShops.length}곳 더)
                  </Button>
                </div>
              )}
            </>
          ) : (
            <EmptyState className="b-panel">
              <h2>{dayLabel(date, today)}에는 조건에 맞는 빈자리가 없어요</h2>
              {nextVacancyDate && !saleOnly ? (
                <>
                  <p>가장 가까운 빈자리는 {dayLabel(nextVacancyDate, today)}에 있어요.</p>
                  <Button
                    variant="primary"
                    onClick={() =>
                      updateOption('date', nextVacancyDate === today ? '' : nextVacancyDate)
                    }
                  >
                    {dayLabel(nextVacancyDate, today)} 빈자리 보기
                  </Button>
                </>
              ) : (
                <p>다른 날짜나 분야를 선택해 보세요.</p>
              )}
              <Button variant={nextVacancyDate && !saleOnly ? 'soft' : 'primary'} onClick={reset}>
                필터 초기화
              </Button>
            </EmptyState>
          )}
        </div>
        <aside className={styles.mapPane}>
          <ShopLocationMap
            shops={shops}
            status={status}
            expandedShopId={expandedId}
            onShowShop={(id) => updateOption('shop', id ? String(id) : '')}
          />
        </aside>
      </div>
    </div>
  );
}
