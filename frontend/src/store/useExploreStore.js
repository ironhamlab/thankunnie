import { create } from 'zustand';
import { exploreApi } from '@/api/exploreApi';
import { toVacancyCard } from '@/utils/exploreVacancy';

// 매장 하나의 하루 빈자리는 많아야 수십 건이라 한 번에 받는다
const SHOP_VACANCY_SIZE = 50;

const fetchVacancyCards = async (params) =>
  (await exploreApi.getVacancies(params)).content.map((item) => toVacancyCard(item));
const fetchShops = async (params) => (await exploreApi.getShops(params)).shops;

// 결과를 dataKey 하나에 담는 조회. 실패하면 빈 목록으로 두고 statusKey 로 알린다.
// 조건을 빠르게 바꾸면 이전 응답이 늦게 와서 현재 결과를 덮을 수 있어 요청 번호로 마지막 요청만 반영한다
const simpleLoader = (set, dataKey, statusKey, fetch) => {
  let seq = 0;
  return async (params) => {
    const current = ++seq;
    set({ [statusKey]: 'loading' });
    try {
      const data = await fetch(params);
      if (current === seq) set({ [dataKey]: data, [statusKey]: 'succeeded' });
    } catch {
      if (current === seq) set({ [dataKey]: [], [statusKey]: 'failed' });
    }
  };
};

export const useExploreStore = create((set, get) => ({
  // 홈 오늘 임박 캐러셀 (C-01, 시술 × 시각)
  imminentVacancies: [],
  imminentStatus: 'idle',

  // 매장 요약 (C-02). 홈 분야 탭 목록·홈 지도·검색 화면 목록과 지도
  homeShops: [],
  homeShopsStatus: 'idle',
  shops: [],
  shopsStatus: 'idle',
  searchShops: [],
  searchShopsStatus: 'idle',

  // 검색 화면에서 펼친 매장의 빈자리 (C-01). { [shopId]: { status, vacancies } }
  shopVacancies: {},

  // 고른 날짜에 빈자리가 없을 때 안내할 가장 가까운 빈자리 날짜(YYYY-MM-DD). 없으면 null
  nextVacancyDate: null,

  categories: [],

  loadImminentVacancies: simpleLoader(
    set,
    'imminentVacancies',
    'imminentStatus',
    fetchVacancyCards,
  ),
  loadHomeShops: simpleLoader(set, 'homeShops', 'homeShopsStatus', fetchShops),
  loadShops: simpleLoader(set, 'shops', 'shopsStatus', fetchShops),

  /** 검색 조건을 바꾸면 매장 목록을 다시 받고, 이전 조건으로 펼쳐 둔 빈자리는 버린다 */
  loadSearchShops: async (params) => {
    set({ shopVacancies: {} });
    await searchShopsLoader(params);
  },

  /** 펼친 매장의 빈자리를 같은 검색 조건으로 받는다 */
  loadShopVacancies: async (shopId, params) => {
    const entry = (patch) =>
      set((state) => ({ shopVacancies: { ...state.shopVacancies, [shopId]: patch } }));
    entry({ status: 'loading', vacancies: [] });
    try {
      const vacancies = await fetchVacancyCards({
        ...params,
        shopId,
        sort: 'START_ASC',
        size: SHOP_VACANCY_SIZE,
      });
      // 그사이 조건이 바뀌어 비워졌으면 반영하지 않는다
      if (get().shopVacancies[shopId]) entry({ status: 'succeeded', vacancies });
    } catch {
      if (get().shopVacancies[shopId]) entry({ status: 'failed', vacancies: [] });
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
  'searchShops',
  'searchShopsStatus',
  fetchShops,
);
