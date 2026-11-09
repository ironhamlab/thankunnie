import { create } from 'zustand';
import { exploreApi } from '@/api/exploreApi';
import { shopDetailApi } from '@/api/shopDetailApi';
import { vacancyDetailApi } from '@/api/vacancyDetailApi';
import { toVacancyCard } from '@/utils/exploreVacancy';

// 매장 하나의 하루 빈자리는 많아야 수십 건이라 한 번에 받는다
const SHOP_VACANCY_SIZE = 50;
// 임박 빈자리 카드와 그 대신 보여줄 매장 수. 임박 빈자리 API 와 같게 둔다
const UPCOMING_SHOP_COUNT = 10;

const fetchVacancyCards = async (params) =>
  (await exploreApi.getVacancies(params)).content.map((item) => toVacancyCard(item));
// 임박 빈자리가 없으면(영업이 끝난 밤 등) 다음으로 빠른 일반 시술 빈자리를 매장마다 1건씩 대신 보여준다
const fetchBookableCards = async () => {
  const imminent = await exploreApi.getImminentVacancies();
  if (imminent.length) return imminent.map((item) => toVacancyCard(item));
  const cards = await fetchVacancyCards({ treatmentType: 'NORMAL', size: SHOP_VACANCY_SIZE });
  const shopIds = new Set();
  return cards
    .filter((card) => !shopIds.has(card.shopId) && shopIds.add(card.shopId))
    .slice(0, UPCOMING_SHOP_COUNT)
    .map((card) => ({ ...card, upcoming: true }));
};
// 목록은 지금 예약할 수 있는 빈자리만 주므로 다른 고객이 결제 중이어도 빠진다. 빠진 카드는 상세로 이유를 확인해
// 결제 중이면 표시를 바꿔 남기고, 예약 확정·닫힘·접수 마감으로 끝났을 때만 imminentClosedIds 에 담아 마감으로 알린다.
// 예약할 수 있는데 빠진 카드(더 빠른 빈자리에 밀려 10개 밖으로 나감)는 알리지 않고 뺀다.
const fetchImminentCards = async () => {
  const bookable = await fetchBookableCards();
  const { imminentVacancies: previous, imminentClosedIds } = useExploreStore.getState();
  const missing = previous.filter((card) => !bookable.some((next) => next.id === card.id));
  const details = await Promise.all(
    missing.map((card) =>
      vacancyDetailApi.getDetail(card.treatmentId, card.startsAt.slice(0, 19)).catch((error) =>
        error.status === 404 ? { bookable: false, notBookableReason: 'REMOVED' } : null,
      ),
    ),
  );
  // 확인하지 못한 카드는 마감으로 단정하지 않고 다음 주기까지 그대로 둔다
  const kept = missing.flatMap((card, index) => {
    const detail = details[index];
    if (!detail) return [card];
    return detail.notBookableReason === 'HELD' ? [{ ...card, held: true }] : [];
  });
  const closedIds = missing
    .filter((_, index) => details[index] && !details[index].bookable)
    .filter((card) => !kept.some((keptCard) => keptCard.id === card.id))
    .map((card) => card.id);
  if (closedIds.length || imminentClosedIds.length) {
    useExploreStore.setState({ imminentClosedIds: closedIds });
  }
  if (!kept.length) return bookable;
  // 결제 중인 동안 같은 매장의 다음 시간이 목록에 올라와도 매장마다 1건만 보여준다
  const keptShopIds = new Set(kept.map((card) => card.shopId));
  const merged = bookable
    .filter((card) => !keptShopIds.has(card.shopId))
    .slice(0, UPCOMING_SHOP_COUNT - kept.length);
  // 보던 카드의 순번이 튀지 않게 원래 자리에 넣는다
  kept.forEach((card) => {
    merged.splice(
      previous.findIndex((item) => item.id === card.id),
      0,
      card,
    );
  });
  return merged;
};
const fetchShops = async (params) => (await exploreApi.getShops(params)).shops;

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '빈자리를 불러오지 못했습니다.',
});

// 펼친 매장 빈자리 요청 번호. 검색 조건이 바뀐 뒤 늦게 온 이전 응답이 새 결과를 덮지 않게 한다
let shopVacancySeq = 0;

// 결과를 dataKey 하나에 담는 조회. 실패하면 빈 목록으로 두고 statusKey·errorKey 로 알린다.
// 조건을 빠르게 바꾸면 이전 응답이 늦게 와서 현재 결과를 덮을 수 있어 요청 번호로 마지막 요청만 반영한다
// refreshable: 주기적으로 다시 받는 값. 한 번 받은 뒤에는 로딩·실패로 바꾸지 않아 화면이 깜빡이지 않고,
// 내용이 같으면 기존 값을 그대로 둬 이 값을 보는 화면이 다시 그려지거나 자동 넘김 타이머가 새로 시작하지 않게 한다
const simpleLoader = (set, keys, fetch, { refreshable = false } = {}) => {
  const { data: dataKey, status: statusKey, error: errorKey } = keys;
  let seq = 0;
  const isLoaded = (state) => refreshable && state[statusKey] === 'succeeded';
  return async (params) => {
    const current = ++seq;
    set((state) => (isLoaded(state) ? {} : { [statusKey]: 'loading', [errorKey]: null }));
    try {
      const data = await fetch(params);
      if (current === seq) {
        set((state) => ({
          [dataKey]:
            refreshable && JSON.stringify(state[dataKey]) === JSON.stringify(data)
              ? state[dataKey]
              : data,
          [statusKey]: 'succeeded',
        }));
      }
    } catch (error) {
      if (current === seq) {
        set((state) =>
          isLoaded(state)
            ? {}
            : {
                ...(refreshable ? {} : { [dataKey]: [] }),
                [statusKey]: 'failed',
                [errorKey]: toError(error),
              },
        );
      }
    }
  };
};

export const useExploreStore = create((set, get) => ({
  // 홈 임박 캐러셀 (임박 빈자리 API, 매장마다 시술 × 시각 1건)
  imminentVacancies: [],
  imminentStatus: 'idle',
  imminentError: null,
  // 이번 갱신에서 예약 확정·닫힘·접수 마감으로 끝나 목록에서 빠진 빈자리 ID
  imminentClosedIds: [],

  // 매장 요약 (C-02). 홈 분야 탭 목록·홈 지도·검색 화면 목록과 지도
  // 빈자리 목록의 전체 날짜 보기에서 빈자리가 없는 매장까지 채울 전체 매장
  allShops: [],
  allShopsStatus: 'idle',
  allShopsError: null,
  // 홈 소개의 빈자리 현황 (분야 구분 없는 매장 요약)
  summaryShops: [],
  summaryShopsStatus: 'idle',
  summaryShopsError: null,
  homeShops: [],
  homeShopsStatus: 'idle',
  homeShopsError: null,
  searchShops: [],
  searchShopsStatus: 'idle',
  searchShopsError: null,
  // 지도 화면(/map)의 지도 범위 매장 요약 (C-02)
  mapPageShops: [],
  mapPageShopsStatus: 'idle',
  mapPageShopsError: null,

  // 검색 화면에서 펼친 매장의 빈자리 (C-01). { [shopId]: { requestId, status, vacancies, error } }
  shopVacancies: {},

  // 고른 날짜에 빈자리가 없을 때 안내할 가장 가까운 빈자리 날짜(YYYY-MM-DD). 없으면 null
  nextVacancyDate: null,

  categories: [],

  loadImminentVacancies: simpleLoader(
    set,
    { data: 'imminentVacancies', status: 'imminentStatus', error: 'imminentError' },
    fetchImminentCards,
    { refreshable: true },
  ),
  loadAllShops: simpleLoader(
    set,
    { data: 'allShops', status: 'allShopsStatus', error: 'allShopsError' },
    exploreApi.getAllShops,
  ),
  loadSummaryShops: simpleLoader(
    set,
    { data: 'summaryShops', status: 'summaryShopsStatus', error: 'summaryShopsError' },
    fetchShops,
    { refreshable: true },
  ),
  loadHomeShops: simpleLoader(
    set,
    { data: 'homeShops', status: 'homeShopsStatus', error: 'homeShopsError' },
    fetchShops,
  ),

  /** 지도 화면: 지도를 옮길 때마다 범위가 바뀌므로 펼쳐 둔 빈자리는 그대로 두고 매장 목록만 다시 받는다 */
  loadMapPageShops: simpleLoader(
    set,
    { data: 'mapPageShops', status: 'mapPageShopsStatus', error: 'mapPageShopsError' },
    fetchShops,
  ),

  /** 지도 화면: 주소로 고른 매장이 지도 범위 밖이면 그 매장 위치로 옮기려고 좌표만 받는다. 못 받으면 null */
  locateShop: async (shopId) => {
    try {
      const shop = await shopDetailApi.getShop(shopId);
      return { lat: shop.latitude, lng: shop.longitude };
    } catch {
      return null;
    }
  },

  /** 필터를 바꾸면 이전 조건으로 펼쳐 둔 빈자리를 버린다 */
  resetShopVacancies: () => set({ shopVacancies: {} }),

  /** 검색 조건을 바꾸면 매장 목록을 다시 받고, 이전 조건으로 펼쳐 둔 빈자리는 버린다 */
  loadSearchShops: async (params) => {
    set({ shopVacancies: {} });
    await searchShopsLoader(params);
  },

  /** 펼친 매장의 빈자리를 같은 검색 조건으로 받는다 */
  loadShopVacancies: async (shopId, params) => {
    const requestId = ++shopVacancySeq;
    const entry = (patch) =>
      set((state) => ({
        shopVacancies: { ...state.shopVacancies, [shopId]: { requestId, ...patch } },
      }));
    // 그사이 조건이 바뀌거나 같은 매장을 다시 요청했으면 이 응답은 버린다
    const isCurrent = () => get().shopVacancies[shopId]?.requestId === requestId;
    entry({ status: 'loading', vacancies: [], error: null });
    try {
      const page = await exploreApi.getVacancies({
        ...params,
        shopId,
        sort: 'START_ASC',
        size: SHOP_VACANCY_SIZE,
      });
      const vacancies = page.content.map((item) => toVacancyCard(item));
      // 개수 제한에 걸려 다 못 받았으면 카드가 매장 페이지에서 나머지를 고르라고 안내한다
      const truncated = page.totalElements > vacancies.length;
      if (isCurrent()) entry({ status: 'succeeded', vacancies, truncated, error: null });
    } catch (error) {
      if (isCurrent()) entry({ status: 'failed', vacancies: [], error: toError(error) });
    }
  },

  /** 같은 조건으로 기간을 넓혀 가장 빨리 시작하는 매장의 날짜를 찾는다. 매장 요약은 가장 빠른 시작순이다 */
  findNextVacancyDate: async (params) => {
    set({ nextVacancyDate: null });
    try {
      const [first] = await fetchShops(params);
      set({ nextVacancyDate: first ? first.earliestStartsAt.slice(0, 10) : null });
    } catch {
      // 안내를 못 해도 빈 결과 화면은 그대로 쓸 수 있다
      set({ nextVacancyDate: null });
    }
  },

  /** 시술 분야 선택지. 한 번 받으면 다시 부르지 않는다 */
  loadCategories: async () => {
    if (get().categories.length) return;
    try {
      // 서버는 이름순으로 주므로 등록 순서(네일·헤어·속눈썹…)인 ID 순으로 보여준다
      const categories = await exploreApi.getCategories();
      set({ categories: [...categories].sort((a, b) => a.categoryId - b.categoryId) });
    } catch {
      // 분야 목록을 못 받아도 '전체'로 검색할 수 있으므로 화면을 막지 않는다
      set({ categories: [] });
    }
  },
}));

const searchShopsLoader = simpleLoader(
  useExploreStore.setState,
  { data: 'searchShops', status: 'searchShopsStatus', error: 'searchShopsError' },
  fetchShops,
);
