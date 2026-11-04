import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useSearchStore } from '@/store/useSearchStore';
import { useExploreStore } from '@/store/useExploreStore';
import { regionNameOf } from '@/constants/regions';
import { dayLabel, validDate } from '@/utils/exploreVacancy';
import { addDays, todayKst } from '@/utils/scheduleDate';
import { distanceKm, formatDistance } from '@/utils/distance';
import { useMyLocation } from '@/hooks/useMyLocation';
import InfoIcon from '@/components/icons/InfoIcon';
import ShopRow from '@/pages/home/components/ShopRow';
import RollingValue from '@/pages/home/components/RollingValue';
import SlidingIndicator from '@/components/common/SlidingIndicator';
import { useSlidingIndicator } from '@/hooks/useSlidingIndicator';
import ShopLocationMap from '@/pages/home/components/ShopLocationMap';
import DateCalendar from '@/pages/home/components/DateCalendar';
import Badge from '@/components/common/Badge';
import Button from '@/components/common/Button';
import EmptyState from '@/components/common/EmptyState';
import styles from './VacancySearch.module.css';

const ALL = '전체';
const ALL_DATES = 'all';
// 매장 정렬. 같은 값이면 매장 ID 순으로 둔다
const SHOP_SORTS = {
  '빠른 시작순': (a, b) => a.earliestStartsAt.localeCompare(b.earliestStartsAt),
  할인율순: (a, b) => b.maxDiscountRate - a.maxDiscountRate,
  '낮은 가격순': (a, b) => a.minSalePrice - b.minSalePrice,
};
// 거리순은 내 위치가 있어야 비교할 수 있어 따로 둔다
const NEAREST = '거리순';
const sorts = [...Object.keys(SHOP_SORTS), NEAREST];
// 휴대폰에서 날짜와 정렬을 한 줄에 둘 때 쓰는 짧은 정렬 이름
const SHORT_SORTS = { '빠른 시작순': '빠른순', '낮은 가격순': '가격순' };
// 한 페이지에 보여줄 매장 수. 펼친 매장의 시간 버튼까지 있으면 길어지므로 8곳씩 나눈다
const PAGE_SIZE = 8;
// 예약은 오늘 포함 60일까지 가능하다
const MAX_DAYS = 60;
// 헤더 검색어는 글자마다 바뀌므로 입력이 멈춘 뒤 조회한다
const KEYWORD_DELAY_MS = 300;
// 고른 날짜에 빈자리가 없을 때 가장 가까운 날짜를 찾아볼 기간(일)
const NEXT_SEARCH_DAYS = 14;
// 매장을 골라 지도까지 올릴 때 헤더와 지도 사이에 띄울 간격(px)
const MAP_SCROLL_GAP = 12;
// 이 폭(px)부터 지도가 목록 옆에 붙는다. VacancySearch.module.css 의 900px 구간과 맞춘다
const SIDE_MAP_MIN_WIDTH = 901;
const MODEL = 'MODEL';

// label 을 hideLabel 로 숨기면 화면에는 버튼만 두고 화면 낭독기에만 이름을 알린다.
// shortLabels 는 휴대폰에서 한 줄에 넣으려고 버튼 글자를 줄일 때 쓰는 짧은 이름이다
function Filter({
  label,
  options,
  value,
  onChange,
  segmented = false,
  hideLabel = false,
  shortLabels,
}) {
  const indicator = useSlidingIndicator(value);
  return (
    <div className={`${styles.filterGroup} ` + (segmented ? styles.segmented : '')}>
      {!hideLabel && <span className={styles.filterLabel}>{label}</span>}
      <div className="b-options" role="group" aria-label={label}>
        {segmented && (
          <SlidingIndicator indicator={indicator} className={styles.segmentIndicator} />
        )}
        {options.map((option) => (
          <button
            key={option}
            ref={value === option ? indicator.selectedRef : null}
            aria-pressed={value === option}
            className={value === option ? 'is-selected' : ''}
            onClick={() => onChange(option)}
          >
            {shortLabels?.[option] ? (
              <>
                <span className={styles.longLabel}>{option}</span>
                <span className={styles.shortLabel}>{shortLabels[option]}</span>
              </>
            ) : (
              option
            )}
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
  const search = useSearchStore((s) => s.search);
  const resetSearch = useSearchStore((s) => s.setSearch);
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
  const [sort, setSort] = useState(sorts[0]);
  // 옆 지도의 내 위치 버튼과 같이 써서, 지도에서 위치를 확인해도 목록에 거리가 보이게 한다
  const myLocation = useMyLocation();
  const { position: myPosition, locating, message: locateMessage, locate } = myLocation;
  // 거리순을 골랐어도 위치를 받기 전에는 기본 정렬 순서로 둔다
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
      priceTier: saleOnly ? 'LAST_MINUTE' : undefined,
      treatmentType: modelOnly ? MODEL : undefined,
      keyword: keyword || undefined,
    }),
    [allDates, today, lastDay, date, categoryId, regionCode, saleOnly, modelOnly, keyword],
  );
  const filterKey = JSON.stringify(filter);
  // 정렬은 조회 조건이 아니지만 목록 순서를 바꾸므로, 정렬이 바뀌어도 첫 페이지로 돌아가게 키에 넣는다
  const pagingKeyOf = (sortName) => `${filterKey}|${sortName}`;
  const pagingKey = pagingKeyOf(sort);

  useEffect(() => {
    if (allDates)
      loadAllShops({ categoryId: categoryId ?? undefined, keyword: keyword || undefined });
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
  // 거리순이면 빈자리가 없는 매장끼리도 가까운 순으로 둔다
  const sortedShops = useMemo(() => {
    const distanceOf = (shop) =>
      distanceKm(myPosition, { lat: shop.latitude, lng: shop.longitude });
    const compare =
      appliedSort === NEAREST ? (a, b) => distanceOf(a) - distanceOf(b) : SHOP_SORTS[appliedSort];
    const withVacancy = [...shops].sort((a, b) => compare(a, b) || a.shopId - b.shopId);
    if (!allDates) return withVacancy;
    const vacancyIds = new Set(shops.map((shop) => shop.shopId));
    const noVacancyShops = allShops.filter(
      (shop) => !vacancyIds.has(shop.shopId) && (!regionCode || shop.regionCode === regionCode),
    );
    if (appliedSort === NEAREST) {
      noVacancyShops.sort((a, b) => distanceOf(a) - distanceOf(b) || a.shopId - b.shopId);
    }
    return [...withVacancy, ...noVacancyShops];
  }, [shops, appliedSort, myPosition, allDates, allShops, regionCode]);
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
  // 지도에서 지금 페이지 매장만 또렷하게 보이게 넘긴다. 한 페이지뿐이면 흐리게 할 매장이 없다.
  // 목록 배열은 그릴 때마다 새로 만들어지므로 매장 ID 문자열이 바뀔 때만 새 Set 을 만든다
  const pageIdsKey = totalPages > 1 ? visibleShops.map((shop) => shop.shopId).join(',') : '';
  const pageShopIds = useMemo(
    () => (pageIdsKey ? new Set(pageIdsKey.split(',')) : undefined),
    [pageIdsKey],
  );
  const goToPage = (next) => {
    setPaging({ key: pagingKey, page: next });
    document.getElementById('vacancy-results')?.scrollIntoView({ block: 'start' });
  };
  // 펼친 매장이 있어도 그 매장 페이지로 가지 않고 첫 페이지를 보이게 새 키와 0페이지를 같이 둔다.
  // 거리순은 고를 때마다 위치를 다시 묻는다(1분 안에 얻은 위치는 다시 쓴다).
  // 처음 묻다 실패하면 정렬도 기본으로 돌린다. 그러지 않으면 나중에 지도에서 위치를 얻을 때 저절로 거리순이 된다.
  // 전에 얻은 위치가 있으면 그 위치로 계속 거리순을 보여준다
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
    // 좁은 화면은 지도가 목록 위에 있어 눌러도 안 보이므로 지도까지 올린다.
    // 위에 붙어 있는 헤더에 지도 윗부분이 가리지 않게 헤더 바로 아래에 맞춘다. 넓은 화면은 지도가 따라오므로 그대로 둔다
    const map = document.getElementById('vacancy-map');
    if (!map) return;
    const headerBottom = document.querySelector('header')?.getBoundingClientRect().bottom ?? 0;
    const { top, bottom } = map.getBoundingClientRect();
    if (top < headerBottom || bottom > window.innerHeight) {
      window.scrollBy({ top: top - headerBottom - MAP_SCROLL_GAP, behavior: 'smooth' });
    }
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
    chooseSort(sorts[0]);
    setParams(
      modelOnly
        ? { view: 'list', type: MODEL }
        : { view: 'list', ...(allDates ? { date: ALL_DATES } : {}) },
      { replace: true },
    );
    resetSearch('');
  };
  const categoryIndicator = useSlidingIndicator(categoryName);
  // 상단 메뉴 셋이 이 화면 하나를 함께 쓰므로, 메뉴를 옮기면 소개 영역을 다시 그려 효과를 낸다
  const menu = modelOnly ? 'model' : saleOnly ? 'sale' : 'search';
  return (
    <div className={`b-container ${styles.home}`}>
      <section key={menu} className={`${styles.hero} ${styles.heroEnter}`}>
        <div className={styles.heroText}>
          {modelOnly ? (
            <h1>
              <em>초보 시술</em>을 받을 수 있는 빈자리
            </h1>
          ) : saleOnly ? (
            <h1>
              시작 <em>3시간 안</em> 초임박 특가 빈자리
            </h1>
          ) : (
            <h1>
              지금 예약 가능한 <em>뷰티 빈자리</em>
            </h1>
          )}
          {/* 휴대폰에서도 제목·설명이 한 줄씩 들어가게 짧게 쓴다 */}
          <p>
            {modelOnly
              ? '포트폴리오·실습용 초보 시술 빈자리를 모았어요.'
              : saleOnly
                ? '곧 시작하는 빈자리를 매장이 할인해 내놓았어요.'
                : '분야와 날짜를 고르면 매장과 위치를 함께 보여드려요.'}
          </p>
        </div>
        {/* 타임세일은 무료 취소가 안 되는 점을 놓치면 안 되므로 제목 옆에 띄운다 */}
        {saleOnly && (
          <span className={styles.saleNote}>
            <InfoIcon size={14} className={styles.noteIcon} />
            {/* 한 줄에 들어가게 취소 정책(constants/cancelPolicy 의 LAST_MINUTE_HOLD)을 줄여 쓴다 */}
            <span>
              시작 3시간 이내 예약은 <strong>무료 취소가 안 돼요</strong>
            </span>
          </span>
        )}
      </section>
      <section
        className={`${styles.filters} b-panel ${styles.filtersEnter}`}
        aria-label="빈자리 검색 필터"
      >
        <div className={styles.categoryRow} role="group" aria-label="시술 분야">
          <SlidingIndicator indicator={categoryIndicator} className={styles.categoryIndicator} />
          {[ALL, ...categories.map((item) => item.name)].map((name) => (
            <button
              key={name}
              ref={categoryName === name ? categoryIndicator.selectedRef : null}
              type="button"
              aria-pressed={categoryName === name}
              onClick={() => chooseCategory(name)}
            >
              {name}
            </button>
          ))}
        </div>
        {/* 날짜와 정렬을 한 줄에 둔다. 폭이 모자라면 정렬이 다음 줄로 내려가고 날짜 쪽이 줄을 채운다 */}
        <div className={`${styles.dateRow} ${saleOnly || modelOnly ? styles.compactRow : ''}`}>
          <div className={styles.dateControls}>
            {!saleOnly && !modelOnly && (
              <Filter
                label="날짜"
                options={[ALL, '날짜 선택']}
                value={allDates ? ALL : '날짜 선택'}
                onChange={(value) => updateOption('date', value === ALL ? ALL_DATES : '')}
                segmented
              />
            )}
            {/* 타임세일은 오늘만 보므로 고를 수 없는 달력 대신 오늘 날짜만 작게 보여준다 */}
            {saleOnly ? (
              <span className={styles.todayChip}>
                <span className={styles.liveDot} aria-hidden="true" />
                오늘 <b>{today.slice(5).replace('-', '.')}</b>
              </span>
            ) : (
              !allDates && (
                <DateCalendar
                  value={date}
                  min={today}
                  max={lastDay}
                  onChange={(value) => updateOption('date', value === today ? '' : value)}
                />
              )
            )}
          </div>
          <Filter
            label="정렬"
            options={sorts}
            value={sort}
            onChange={chooseSort}
            segmented
            hideLabel
            shortLabels={SHORT_SORTS}
          />
        </div>
        {(locating || locateMessage) && (
          <p className={styles.locateMessage} role="status">
            {locating ? '내 위치를 확인하는 중이에요…' : locateMessage}
          </p>
        )}
      </section>
      <div id="vacancy-results" className={`b-row ${styles.resultsHeading}`}>
        <h2>
          {allDates && allShopsStatus !== 'failed' ? allDatesTitle : listTitle}{' '}
          <Badge>
            <RollingValue value={`${sortedShops.length}곳`} />
          </Badge>
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
              {/* 정렬·페이지만 바꾸면 남아 있는 카드는 새로 그려지지 않아 새로 들어온 카드만 움직인다.
                  전부 같이 움직이도록 목록을 새로 그린다. 조회 조건을 바꾸면 불러오는 동안 이미 새로 그려진다 */}
              <div
                key={`${appliedSort}|${page}`}
                className={`${styles.rows} ${styles.rowsEnter} ${saleOnly ? styles.saleRows : ''}`}
              >
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
                      weekly={allDates}
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
            pageShopIds={pageShopIds}
            weekly={allDates}
            onPinShop={(id) => {
              // 지도에서 고른 매장이 다른 페이지에 있으면 그 페이지로 넘긴다
              setPaging({ key: pagingKey, page: pageOf(id) });
              updateOption('pin', String(id));
              // 지도가 목록 옆에 있는 넓은 화면에서만 그 카드가 보이게 목록을 옮긴다.
              // 좁은 화면은 지도와 목록이 위아래라 옮기면 보던 지도가 화면 밖으로 나간다
              if (window.matchMedia?.(`(min-width: ${SIDE_MAP_MIN_WIDTH}px)`).matches) {
                // 페이지가 바뀌면 카드가 다시 그려진 뒤에 찾아야 하므로 한 번 미룬다
                window.setTimeout(() =>
                  document
                    .getElementById(`shop-row-${id}`)
                    ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }),
                );
              }
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
