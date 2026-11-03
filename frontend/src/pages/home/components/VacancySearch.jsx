import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { GRACE_CANCEL_MESSAGE } from '@/constants/cancelPolicy';
import { useDesignStore } from '@/store/useDesignStore';
import { useExploreStore } from '@/store/useExploreStore';
import { regionNameOf } from '@/constants/regions';
import { TIME_OF_DAY, dayLabel, validDate } from '@/utils/exploreVacancy';
import { addDays, todayKst } from '@/utils/scheduleDate';
import { distanceKm, formatDistance } from '@/utils/distance';
import { useMyLocation } from '@/hooks/useMyLocation';
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
const ALL_DATES = 'all';
const times = [ALL, ...Object.keys(TIME_OF_DAY)];
// 매장 정렬. 같은 값이면 매장 ID 순으로 둔다
const SHOP_SORTS = {
  '빠른 시작순': (a, b) => a.earliestStartsAt.localeCompare(b.earliestStartsAt),
  할인율순: (a, b) => b.maxDiscountRate - a.maxDiscountRate,
  '낮은 가격순': (a, b) => a.minSalePrice - b.minSalePrice,
};
// 가까운순은 내 위치가 있어야 비교할 수 있어 따로 둔다
const NEAREST = '가까운순';
const sorts = [...Object.keys(SHOP_SORTS), NEAREST];
// 한 페이지에 보여줄 매장 수. 펼친 매장의 시간 버튼까지 있으면 길어지므로 8곳씩 나눈다
const PAGE_SIZE = 8;
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
 * 매장 줄의 지도에서 보기나 시간 보기를 누르면 옆 지도가 그 매장으로 확대해 옮긴다.
 * 분야·지역·날짜·펼친 매장·지도에서 고른 매장·타임세일·초보 시술 모집 여부는 주소에 남겨 새로고침이나 홈에서 들어와도 같은 화면을 보여준다.
 */
export default function VacancySearch() {
  const [params, setParams] = useSearchParams();
  const today = todayKst();
  const lastDay = addDays(today, MAX_DAYS - 1);
  const saleOnly = params.get('sale') === '1';
  const modelOnly = params.get('type') === MODEL;
  const expandedId = params.get('shop');
  // 지도에서 보기로 고른 매장. 펼친 매장보다 먼저 옆 지도에 보여준다
  const pinnedId = params.get('pin');
  const mapShopId = pinnedId ?? expandedId;
  const categoryId = params.get('category');
  // 알림 링크의 region. 없으면 지역으로 좁히지 않는다
  const regionCode = params.get('region') || undefined;
  const dateParam = params.get('date');
  // 타임세일은 날짜 대신 지금부터 3시간 안을 보고, 그 밖에는 고른 날짜(기본 오늘)를 본다
  const date = (!saleOnly && validDate(dateParam, today, lastDay)) || today;
  // 전체 날짜(date=all)는 예약 가능한 기간 전체에서 빈자리가 있는 매장을 모아 본다
  const allDates = !saleOnly && dateParam === ALL_DATES;
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
  const error = useExploreStore((s) => s.searchShopsError);
  const allShops = useExploreStore((s) => s.allShops);
  const allShopsStatus = useExploreStore((s) => s.allShopsStatus);
  const loadAllShops = useExploreStore((s) => s.loadAllShops);
  const shopVacancies = useExploreStore((s) => s.shopVacancies);
  const categories = useExploreStore((s) => s.categories);
  const loadSearchShops = useExploreStore((s) => s.loadSearchShops);
  const loadShopVacancies = useExploreStore((s) => s.loadShopVacancies);
  const loadCategories = useExploreStore((s) => s.loadCategories);
  const nextVacancyDate = useExploreStore((s) => s.nextVacancyDate);
  const findNextVacancyDate = useExploreStore((s) => s.findNextVacancyDate);
  const [time, setTime] = useState(ALL);
  const [sort, setSort] = useState(sorts[0]);
  // 옆 지도의 내 위치 버튼과 같이 써서, 지도에서 위치를 확인해도 목록에 거리가 보이게 한다
  const myLocation = useMyLocation();
  const { position: myPosition, locating, message: locateMessage, locate } = myLocation;
  // 가까운순을 골랐어도 위치를 받기 전에는 기본 정렬 순서로 둔다
  const appliedSort = sort === NEAREST && !myPosition ? sorts[0] : sort;
  const [keyword, setKeyword] = useState(search);
  const [paging, setPaging] = useState({ key: '', page: 0 });
  // 목록에서 매장을 고를 때마다 새 값을 넣어, 같은 매장을 다시 눌러도 지도를 다시 확대한다
  const [mapFocus, setMapFocus] = useState(null);
  const categoryName =
    categories.find((item) => String(item.categoryId) === categoryId)?.name ?? ALL;
  // 주소에 region이 있을 때만 제목 앞에 구 이름을 붙인다. 11680 → 강남구
  const regionName = regionCode ? regionNameOf(regionCode) : '';
  let listTitle = '빈자리 있는 매장';
  if (regionName && categoryName !== ALL) {
    listTitle = `${regionName} ${categoryName} 빈자리 있는 매장`;
  } else if (regionName) {
    listTitle = `${regionName} 빈자리 있는 매장`;
  } else if (categoryName !== ALL) {
    listTitle = `${categoryName} 빈자리 있는 매장`;
  }
  const allDatesTitleParts = [regionName, categoryName === ALL ? '' : categoryName].filter(Boolean);
  const allDatesTitle = `${allDatesTitleParts.join(' ') || '전체'} 매장`;

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  useEffect(() => {
    const timer = window.setTimeout(() => setKeyword(search.trim()), KEYWORD_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [search]);

  // 매장 목록·지도·펼친 매장의 빈자리가 같이 쓰는 조건
  // 타임세일은 날짜를 보내지 않는다. 서버가 영업시간 기준으로 시작 3시간 이내(자정 넘어 새벽 포함)를 본다
  const filter = useMemo(
    () => ({
      startDate: saleOnly ? undefined : allDates ? today : date,
      endDate: saleOnly ? undefined : allDates ? lastDay : date,
      categoryId: categoryId ?? undefined,
      regionCode,
      timeOfDay: TIME_OF_DAY[time],
      priceTier: saleOnly ? 'LAST_MINUTE' : undefined,
      treatmentType: modelOnly ? MODEL : undefined,
      keyword: keyword || undefined,
    }),
    [allDates, today, lastDay, date, categoryId, regionCode, time, saleOnly, modelOnly, keyword],
  );
  const filterKey = JSON.stringify(filter);
  // 정렬은 조회 조건이 아니지만 목록 순서를 바꾸므로, 정렬이 바뀌어도 첫 페이지로 돌아가게 키에 넣는다
  const pagingKeyOf = (sortName) => `${filterKey}|${sortName}`;
  const pagingKey = pagingKeyOf(sort);

  useEffect(() => {
    if (allDates) loadAllShops({ categoryId: categoryId ?? undefined, keyword: keyword || undefined });
  }, [allDates, categoryId, keyword, loadAllShops]);

  useEffect(() => {
    loadSearchShops(filter);
  }, [filter, loadSearchShops]);

  // 하루씩 보므로 빈 날짜를 자주 만난다. 같은 조건으로 다음 날부터 찾아 가장 가까운 날짜를 안내한다
  // 타임세일은 오늘만 보는 화면이라 다른 날짜를 안내하지 않는다
  const noVacancy = status === 'succeeded' && shops.length === 0;
  // 전체 날짜 보기는 다른 날짜를 찾지 않으므로, 이전 검색에서 찾아 둔 날짜를 안내하지 않는다
  const suggestNextDate = nextVacancyDate && !saleOnly && !allDates;
  useEffect(() => {
    if (!noVacancy || saleOnly || allDates || date >= lastDay) return;
    const end = addDays(date, NEXT_SEARCH_DAYS);
    findNextVacancyDate({
      ...filter,
      startDate: addDays(date, 1),
      endDate: end > lastDay ? lastDay : end,
    });
  }, [noVacancy, saleOnly, allDates, date, lastDay, filter, findNextVacancyDate]);

  const detail = expandedId ? shopVacancies[expandedId] : undefined;
  useEffect(() => {
    if (expandedId && !detail && status === 'succeeded') {
      loadShopVacancies(expandedId, filter);
    }
  }, [expandedId, detail, status, filter, loadShopVacancies]);

  // 지도나 홈에서 고른 매장이 화면 밖이면 보이도록 옮긴다
  useEffect(() => {
    if (expandedId && status === 'succeeded') {
      document.getElementById(`shop-row-${expandedId}`)?.scrollIntoView({ block: 'nearest' });
    }
  }, [expandedId, status]);

  // 전체 날짜 보기는 빈자리가 있는 매장을 먼저 두고, 빈자리가 없는 매장을 뒤에 붙인다.
  // 시작 시간을 고르면 그 시간 빈자리를 찾는 것이므로 빈자리가 없는 매장은 빼고 본다
  // 가까운순이면 빈자리가 없는 매장끼리도 가까운 순으로 둔다
  const sortedShops = useMemo(() => {
    const distanceOf = (shop) =>
      distanceKm(myPosition, { lat: shop.latitude, lng: shop.longitude });
    const compare =
      appliedSort === NEAREST
        ? (a, b) => distanceOf(a) - distanceOf(b)
        : SHOP_SORTS[appliedSort];
    const withVacancy = [...shops].sort((a, b) => compare(a, b) || a.shopId - b.shopId);
    if (!allDates || time !== ALL) return withVacancy;
    const vacancyIds = new Set(shops.map((shop) => shop.shopId));
    const noVacancyShops = allShops.filter(
      (shop) =>
        !vacancyIds.has(shop.shopId) && (!regionCode || shop.regionCode === regionCode),
    );
    if (appliedSort === NEAREST) {
      noVacancyShops.sort((a, b) => distanceOf(a) - distanceOf(b) || a.shopId - b.shopId);
    }
    return [...withVacancy, ...noVacancyShops];
  }, [shops, appliedSort, myPosition, allDates, time, allShops, regionCode]);
  // 위치를 받은 뒤에는 정렬과 상관없이 매장마다 거리를 보여준다
  const distanceLabelOf = (shop) =>
    myPosition
      ? formatDistance(distanceKm(myPosition, { lat: shop.latitude, lng: shop.longitude }))
      : undefined;
  // 조건이 바뀌면 첫 페이지로 돌아간다. 주소로 고른 매장이 있으면 그 매장이 있는 페이지를 연다
  const pageOf = (shopId) => {
    const index = sortedShops.findIndex((shop) => String(shop.shopId) === String(shopId));
    return index < 0 ? 0 : Math.floor(index / PAGE_SIZE);
  };
  const totalPages = Math.ceil(sortedShops.length / PAGE_SIZE);
  const page = Math.min(
    paging.key === pagingKey ? paging.page : pageOf(mapShopId),
    Math.max(totalPages - 1, 0),
  );
  const visibleShops = sortedShops.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const goToPage = (next) => {
    setPaging({ key: pagingKey, page: next });
    document.getElementById('vacancy-results')?.scrollIntoView({ block: 'start' });
  };
  // 펼친 매장이 있어도 그 매장 페이지로 가지 않고 첫 페이지를 보이게 새 키와 0페이지를 같이 둔다.
  // 가까운순은 고를 때마다 위치를 다시 묻는다(1분 안에 얻은 위치는 다시 쓴다).
  // 처음 묻다 실패하면 정렬도 기본으로 돌린다. 그러지 않으면 나중에 지도에서 위치를 얻을 때 저절로 가까운순이 된다.
  // 전에 얻은 위치가 있으면 그 위치로 계속 가까운순을 보여준다
  const chooseSort = (sortName) => {
    setSort(sortName);
    setPaging({ key: pagingKeyOf(sortName), page: 0 });
    if (sortName !== NEAREST) return;
    const hadPosition = Boolean(myPosition);
    locate().then((found) => {
      if (!found && !hadPosition) {
        setSort((current) => (current === NEAREST ? sorts[0] : current));
      }
    });
  };

  const focusMap = (shop) => {
    setMapFocus({ lat: shop.latitude, lng: shop.longitude, zoom: true });
    // 좁은 화면은 지도가 목록 위에 있어 눌러도 안 보이므로 지도까지 올린다
    document.getElementById('vacancy-map')?.scrollIntoView({ block: 'nearest' });
  };
  // 시간 보기로 펼치면 지도에서 보기로 고른 매장 대신 펼친 매장을 보여준다
  const toggleShop = (shop) => {
    const shopId = String(shop.shopId);
    const expanding = shopId !== expandedId;
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        if (expanding) next.set('shop', shopId);
        else next.delete('shop');
        next.delete('pin');
        return next;
      },
      { replace: true },
    );
    if (expanding) focusMap(shop);
  };
  const locateShop = (shop) => {
    updateOption('pin', String(shop.shopId));
    focusMap(shop);
  };
  const chooseCategory = (name) => {
    const id = categories.find((item) => item.name === name)?.categoryId;
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        if (id) next.set('category', id);
        else next.delete('category');
        // 분야를 바꾸면 펼쳐 두거나 지도에서 고른 매장은 목록에 없을 수 있다
        next.delete('shop');
        next.delete('pin');
        next.set('view', 'list');
        return next;
      },
      { replace: true },
    );
  };
  const reset = () => {
    setTime(ALL);
    chooseSort(sorts[0]);
    setParams(
      modelOnly
        ? { view: 'list', type: MODEL }
        : { view: 'list', ...(allDates ? { date: ALL_DATES } : {}) },
      { replace: true },
    );
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
              <em>초보 시술</em>을 받을 수 있는 빈자리
            </h1>
          ) : (
            <h1>
              지금 예약 가능한 <em>뷰티 빈자리</em>를 찾아보세요
            </h1>
          )}
          <p>
            {modelOnly
              ? '포트폴리오 촬영·실습을 위한 초보 시술을 제공하는 매장의 빈자리예요.'
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
          {!saleOnly && !modelOnly && (
            <Filter
              label="날짜"
              options={[ALL, '날짜 선택']}
              value={allDates ? ALL : '날짜 선택'}
              onChange={(value) => updateOption('date', value === ALL ? ALL_DATES : '')}
              segmented
            />
          )}
          {!allDates && (
            <DateCalendar
              value={date}
              min={today}
              max={lastDay}
              disabled={saleOnly}
              onChange={(value) => updateOption('date', value === today ? '' : value)}
            />
          )}
          <Filter
            label="예약 종류"
            options={[ALL, '타임세일']}
            value={saleOnly ? '타임세일' : ALL}
            onChange={(value) => updateOption('sale', value === '타임세일' ? '1' : '')}
            segmented
          />
        </div>
        {saleOnly && (
          <p className={styles.description}>
            시작 3시간 안에 할인 중인 초임박 특가 빈자리만 보여드려요.{' '}
            {GRACE_CANCEL_MESSAGE.LAST_MINUTE_HOLD}
          </p>
        )}
        <div className={`b-row ${styles.filterLine}`}>
          <Filter label="시작 시간" options={times} value={time} onChange={setTime} segmented />
          <Filter label="정렬" options={sorts} value={sort} onChange={chooseSort} segmented />
        </div>
        {(locating || locateMessage) && (
          <p className={styles.locateMessage} role="status">
            {locating ? '내 위치를 확인하는 중이에요…' : locateMessage}
          </p>
        )}
      </section>
      <div id="vacancy-results" className={`b-row ${styles.resultsHeading}`}>
        <h2>
          {allDates && allShopsStatus !== 'failed' ? allDatesTitle : listTitle} <Badge>{sortedShops.length}곳</Badge>
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
              <p>{error?.message ?? '잠시 후 다시 시도해 주세요.'}</p>
              <Button variant="primary" onClick={() => loadSearchShops(filter)}>
                다시 시도
              </Button>
            </EmptyState>
          ) : sortedShops.length ? (
            <>
              <div className={styles.rows}>
                {visibleShops.map((shop) => (
                  <div key={shop.shopId} id={`shop-row-${shop.shopId}`}>
                    <ShopRow
                      shop={shop}
                      expanded={String(shop.shopId) === expandedId}
                      detail={String(shop.shopId) === expandedId ? detail : undefined}
                      onToggle={() => toggleShop(shop)}
                      onRetry={() => loadShopVacancies(String(shop.shopId), filter)}
                      model={modelOnly}
                      onLocate={locateShop}
                      located={String(shop.shopId) === mapShopId}
                      distance={distanceLabelOf(shop)}
                    />
                  </div>
                ))}
              </div>
              {totalPages > 1 && (
                <nav className={styles.pagination} aria-label="매장 목록 페이지">
                  <Button size="small" disabled={page === 0} onClick={() => goToPage(page - 1)}>
                    이전
                  </Button>
                  {Array.from({ length: totalPages }, (_, index) => (
                    <Button
                      key={index}
                      size="small"
                      variant={index === page ? 'primary' : undefined}
                      aria-current={index === page ? 'page' : undefined}
                      onClick={() => goToPage(index)}
                    >
                      {index + 1}
                    </Button>
                  ))}
                  <Button
                    size="small"
                    disabled={page === totalPages - 1}
                    onClick={() => goToPage(page + 1)}
                  >
                    다음
                  </Button>
                </nav>
              )}
            </>
          ) : saleOnly ? (
            <EmptyState className="b-panel">
              <h2>지금 세일 중인 자리가 없어요</h2>
              <p>시작 3시간 전부터 할인하는 자리가 생기면 여기에 보여드려요.</p>
              <Button as={Link} variant="primary" to={`${ROUTES.HOME}?view=list&date=all`}>
                일반 예약하기
              </Button>
            </EmptyState>
          ) : (
            <EmptyState className="b-panel">
              <h2>
                {allDates ? '예약 가능한 기간' : dayLabel(date, today)}에는 조건에 맞는 빈자리가
                없어요
              </h2>
              {suggestNextDate ? (
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
              <Button variant={suggestNextDate ? 'soft' : 'primary'} onClick={reset}>
                필터 초기화
              </Button>
            </EmptyState>
          )}
        </div>
        <aside id="vacancy-map" className={styles.mapPane}>
          <ShopLocationMap
            shops={allDates ? sortedShops : shops}
            status={status}
            expandedShopId={expandedId}
            pinnedShopId={pinnedId}
            focusPosition={mapFocus}
            myLocation={myLocation}
            onPinShop={(id) => {
              // 지도에서 고른 매장이 다른 페이지에 있으면 그 페이지로 넘긴다
              setPaging({ key: pagingKey, page: pageOf(id) });
              updateOption('pin', String(id));
            }}
            onShowShop={(id) => {
              // 지도에서 고른 매장이 다른 페이지에 있으면 그 페이지로 넘긴다
              if (id) setPaging({ key: pagingKey, page: pageOf(id) });
              updateOption('shop', id ? String(id) : '');
            }}
            model={modelOnly}
          />
        </aside>
      </div>
    </div>
  );
}
