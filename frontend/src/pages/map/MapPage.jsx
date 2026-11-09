import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '@/constants/routes';
import { useExploreStore } from '@/store/useExploreStore';
import { addDays, todayKst } from '@/utils/scheduleDate';
import { validDate } from '@/utils/exploreVacancy';
import ShopRow from '@/components/shop/ShopRow';
import DateCalendar from '@/components/common/DateCalendar';
import ExploreMap from '@/components/map/ExploreMap';
import EmptyState from '@/components/common/EmptyState';
import Button from '@/components/common/Button';
import styles from './MapPage.module.css';

const ALL = '전체';
// 예약은 오늘 포함 60일까지 가능하다
const MAX_DAYS = 60;

/**
 * 지도 메인 화면 (/map). 지도 범위 안에서 빈자리가 있는 매장을 핀(C-02)으로 보여주고,
 * 옆 목록에서 매장을 펼치면 시술별 시작 시각(C-01)을, 매장을 누르면 매장 상세를 연다.
 * 분야·날짜·타임세일·고른 매장은 주소에 남겨 새로고침해도 같은 화면을 보여준다. 지도 범위는 남기지 않는다.
 * 카카오맵을 쓸 수 없으면 지도 없이 범위 제한 없는 매장 목록만 보여준다.
 */
export default function MapPage() {
  const [params, setParams] = useSearchParams();
  const today = todayKst();
  const lastDay = addDays(today, MAX_DAYS - 1);
  const saleOnly = params.get('sale') === '1';
  const selectedId = params.get('shop');
  const categoryId = params.get('category');
  const dateParam = params.get('date');
  // 타임세일은 날짜 대신 지금부터 3시간 안을 보고, 그 밖에는 고른 날짜(기본 오늘)를 본다
  const date = (!saleOnly && validDate(dateParam, today, lastDay)) || today;
  const shops = useExploreStore((s) => s.mapPageShops);
  const status = useExploreStore((s) => s.mapPageShopsStatus);
  const error = useExploreStore((s) => s.mapPageShopsError);
  const shopVacancies = useExploreStore((s) => s.shopVacancies);
  const categories = useExploreStore((s) => s.categories);
  const loadMapPageShops = useExploreStore((s) => s.loadMapPageShops);
  const loadShopVacancies = useExploreStore((s) => s.loadShopVacancies);
  const resetShopVacancies = useExploreStore((s) => s.resetShopVacancies);
  const loadCategories = useExploreStore((s) => s.loadCategories);
  const locateShop = useExploreStore((s) => s.locateShop);
  const [bounds, setBounds] = useState(null);
  const [mapUnavailable, setMapUnavailable] = useState(false);
  const [focusPosition, setFocusPosition] = useState(null);
  // 새로고침·공유 주소로 들어왔을 때 고른 매장. 지도 범위는 주소에 남기지 않으므로 처음 한 번만 그 매장 위치를 찾는다
  const restoreShopIdRef = useRef(selectedId);
  const categoryName =
    categories.find((item) => String(item.categoryId) === categoryId)?.name ?? ALL;

  useEffect(() => {
    loadCategories();
  }, [loadCategories]);

  // 지도 범위를 뺀 조건. 매장 목록·펼친 매장의 빈자리가 같이 쓴다
  const filter = useMemo(
    () => ({
      // 타임세일은 날짜를 보내지 않는다. 서버가 영업시간 기준으로 시작 3시간 이내(자정 넘어 새벽 포함)를 본다
      startDate: saleOnly ? undefined : date,
      endDate: saleOnly ? undefined : date,
      categoryId: categoryId ?? undefined,
      priceTier: saleOnly ? 'LAST_MINUTE' : undefined,
    }),
    [date, categoryId, saleOnly],
  );

  // 필터가 바뀌면 이전 조건으로 펼쳐 둔 빈자리는 버린다
  useEffect(() => {
    resetShopVacancies();
  }, [filter, resetShopVacancies]);

  // 지도가 첫 범위를 알려 주기 전에는 조회하지 않는다. 지도를 못 쓰면 범위 없이 조회한다
  useEffect(() => {
    if (!bounds && !mapUnavailable) return;
    loadMapPageShops({ ...filter, ...(mapUnavailable ? {} : bounds) });
  }, [filter, bounds, mapUnavailable, loadMapPageShops]);

  // 첫 조회 결과에 고른 매장이 없으면 그 매장 위치로 지도를 옮긴다. 옮기면 그 범위로 다시 조회해 목록에서 펼쳐진다.
  // 한 번만 시도하므로 응답을 기다리는 사이 결과가 바뀌어도 버리지 않는다
  useEffect(() => {
    const shopId = restoreShopIdRef.current;
    if (!shopId || status !== 'succeeded' || mapUnavailable) return;
    restoreShopIdRef.current = null;
    if (shops.some((shop) => String(shop.shopId) === shopId)) return;
    locateShop(shopId).then((position) => {
      if (position) setFocusPosition(position);
    });
  }, [status, shops, mapUnavailable, locateShop]);

  const detail = selectedId ? shopVacancies[selectedId] : undefined;
  useEffect(() => {
    if (selectedId && !detail && status === 'succeeded') loadShopVacancies(selectedId, filter);
  }, [selectedId, detail, status, filter, loadShopVacancies]);

  // 지도에서 고른 매장이 목록에서 보이도록 옮긴다
  useEffect(() => {
    if (selectedId) {
      document.getElementById(`map-shop-${selectedId}`)?.scrollIntoView({ block: 'nearest' });
    }
  }, [selectedId, shops]);

  const sortedShops = useMemo(
    () =>
      [...shops].sort(
        (a, b) => a.earliestStartsAt.localeCompare(b.earliestStartsAt) || a.shopId - b.shopId,
      ),
    [shops],
  );

  const updateParams = useCallback(
    (changes) => {
      setParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          Object.entries(changes).forEach(([key, value]) => {
            if (value) next.set(key, value);
            else next.delete(key);
          });
          return next;
        },
        { replace: true },
      );
    },
    [setParams],
  );
  // 필터를 바꾸면 열려 있던 매장은 닫는다 (docs/api/C-01-vacancy-search.md 6장)
  const updateFilter = (changes) => updateParams({ ...changes, shop: '' });
  const selectShop = useCallback(
    (shopId) => updateParams({ shop: shopId === selectedId ? '' : shopId }),
    [updateParams, selectedId],
  );
  const handleUnavailable = useCallback(() => setMapUnavailable(true), []);

  return (
    <div className={`b-container ${styles.root}`}>
      <header className={styles.header}>
        <div>
          <h1>지도로 빈자리 찾기</h1>
          <p>지도를 움직이면 그 지역에서 빈자리가 있는 매장을 다시 찾아요.</p>
        </div>
        <div className={styles.filters}>
          <div className={styles.categoryRow} role="group" aria-label="시술 분야">
            {[{ categoryId: '', name: ALL }, ...categories].map((item) => (
              <button
                key={item.name}
                type="button"
                aria-pressed={categoryName === item.name}
                onClick={() => updateFilter({ category: String(item.categoryId) })}
              >
                {item.name}
              </button>
            ))}
          </div>
          <div className={styles.dateRow}>
            <DateCalendar
              value={date}
              min={today}
              max={lastDay}
              disabled={saleOnly}
              onChange={(value) => updateFilter({ date: value === today ? '' : value })}
            />
            <button
              type="button"
              className={styles.saleToggle}
              aria-pressed={saleOnly}
              onClick={() => updateFilter({ sale: saleOnly ? '' : '1' })}
            >
              ϟ 타임세일만
            </button>
          </div>
        </div>
      </header>

      <div className={`${styles.layout} ${mapUnavailable ? styles.listOnly : ''}`}>
        {!mapUnavailable && (
          <section className={styles.mapPane} aria-label="빈자리 매장 지도">
            <ExploreMap
              shops={shops}
              selectedId={selectedId}
              onSelect={selectShop}
              onBoundsChange={setBounds}
              onUnavailable={handleUnavailable}
              focusPosition={focusPosition}
            />
            {status === 'loading' && <span className={styles.refreshing}>매장을 찾는 중…</span>}
          </section>
        )}
        <aside className={styles.listPane} aria-label="이 지역 빈자리 매장">
          <h2>
            {mapUnavailable ? '빈자리 있는 매장' : '이 지역 빈자리 매장'}{' '}
            <span>{sortedShops.length}곳</span>
          </h2>
          {mapUnavailable && (
            <p className={styles.notice}>지도를 불러오지 못해 전체 매장을 목록으로 보여드려요.</p>
          )}
          {status === 'failed' ? (
            <EmptyState role="alert">
              <p>{error?.message ?? '빈자리를 불러오지 못했어요.'}</p>
              <Button
                variant="primary"
                onClick={() => loadMapPageShops({ ...filter, ...(mapUnavailable ? {} : bounds) })}
              >
                다시 시도
              </Button>
            </EmptyState>
          ) : status === 'succeeded' && sortedShops.length === 0 && saleOnly ? (
            <EmptyState>
              <p>지금 세일 중인 자리가 없어요.</p>
              <Button as={Link} variant="primary" to={`${ROUTES.HOME}?view=list&date=all`}>
                일반 예약하기
              </Button>
            </EmptyState>
          ) : status === 'succeeded' && sortedShops.length === 0 ? (
            <EmptyState>
              <p>이 지역에는 조건에 맞는 빈자리가 없어요.</p>
              <p className={styles.notice}>지도를 옮기거나 축소해 보세요.</p>
            </EmptyState>
          ) : (
            <div className={styles.rows}>
              {sortedShops.map((shop) => (
                <div key={shop.shopId} id={`map-shop-${shop.shopId}`}>
                  <ShopRow
                    shop={shop}
                    expanded={String(shop.shopId) === selectedId}
                    detail={String(shop.shopId) === selectedId ? detail : undefined}
                    onToggle={() => selectShop(String(shop.shopId))}
                    onRetry={() => loadShopVacancies(String(shop.shopId), filter)}
                  />
                </div>
              ))}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
