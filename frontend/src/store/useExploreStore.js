import { create } from 'zustand';
import { exploreApi } from '@/api/exploreApi';
import { toVacancyCard } from '@/utils/exploreVacancy';

const PAGE_SIZE = 20;

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '빈자리를 불러오지 못했습니다.',
});

// 필터를 빠르게 바꾸면 이전 요청 응답이 늦게 와서 현재 결과를 덮을 수 있어 요청 번호로 마지막 요청만 반영한다
const requestSeq = { search: 0, home: 0, shops: 0 };

export const useExploreStore = create((set, get) => ({
  // 검색 화면 목록 (C-01)
  vacancies: [],
  totalElements: 0,
  page: 0,
  hasNext: false,
  params: null,
  status: 'idle',
  error: null,

  // 홈 타임세일 목록 (C-01)
  homeVacancies: [],
  homeStatus: 'idle',

  // 홈 지도 매장 요약 (C-02)
  shops: [],
  shopsStatus: 'idle',

  categories: [],

  /** 조건을 바꿔 첫 페이지부터 다시 조회한다 */
  searchVacancies: async (params) => {
    const seq = ++requestSeq.search;
    set({ params, status: 'loading', error: null });
    try {
      const result = await exploreApi.getVacancies({ ...params, page: 0, size: PAGE_SIZE });
      if (seq !== requestSeq.search) return;
      set({
        vacancies: result.content.map((item) => toVacancyCard(item)),
        totalElements: result.totalElements,
        page: 0,
        hasNext: result.hasNext,
        status: 'succeeded',
      });
    } catch (error) {
      if (seq !== requestSeq.search) return;
      set({
        vacancies: [],
        totalElements: 0,
        hasNext: false,
        status: 'failed',
        error: toError(error),
      });
    }
  },

  /** 같은 조건으로 다음 페이지를 이어 붙인다 */
  loadMoreVacancies: async () => {
    const { params, page, hasNext, status } = get();
    if (!hasNext || status === 'loading' || status === 'loadingMore') return;
    const seq = ++requestSeq.search;
    set({ status: 'loadingMore', error: null });
    try {
      const result = await exploreApi.getVacancies({ ...params, page: page + 1, size: PAGE_SIZE });
      if (seq !== requestSeq.search) return;
      set((state) => ({
        vacancies: [...state.vacancies, ...result.content.map((item) => toVacancyCard(item))],
        totalElements: result.totalElements,
        page: page + 1,
        hasNext: result.hasNext,
        status: 'succeeded',
      }));
    } catch (error) {
      if (seq !== requestSeq.search) return;
      set({ status: 'succeeded', error: toError(error) });
    }
  },

  loadHomeVacancies: async (params) => {
    const seq = ++requestSeq.home;
    set({ homeStatus: 'loading' });
    try {
      const result = await exploreApi.getVacancies(params);
      if (seq !== requestSeq.home) return;
      set({
        homeVacancies: result.content.map((item) => toVacancyCard(item)),
        homeStatus: 'succeeded',
      });
    } catch {
      if (seq !== requestSeq.home) return;
      set({ homeVacancies: [], homeStatus: 'failed' });
    }
  },

  loadShops: async (params) => {
    const seq = ++requestSeq.shops;
    set({ shopsStatus: 'loading' });
    try {
      const result = await exploreApi.getShops(params);
      if (seq !== requestSeq.shops) return;
      set({ shops: result.shops, shopsStatus: 'succeeded' });
    } catch {
      if (seq !== requestSeq.shops) return;
      set({ shops: [], shopsStatus: 'failed' });
    }
  },

  /** 시술 분야 선택지. 한 번 받으면 다시 부르지 않는다 */
  loadCategories: async () => {
    if (get().categories.length) return;
    try {
      set({ categories: await exploreApi.getCategories() });
    } catch {
      // 분야 목록을 못 받아도 '전체'로 검색할 수 있으므로 화면을 막지 않는다
      set({ categories: [] });
    }
  },
}));
