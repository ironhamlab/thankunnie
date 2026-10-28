import { create } from 'zustand';
import { reservationApi } from '@/api/reservationApi';

// 사장님 예약 현황(owner)과 고객 내 예약(my)이 같은 응답 모양을 쓰므로 조회 범위만 바꿔 호출한다.
const API_BY_SCOPE = {
  owner: { list: reservationApi.getOwnerReservations, detail: reservationApi.getOwnerReservation },
  my: { list: reservationApi.getMyReservations, detail: reservationApi.getMyReservation },
};

const EMPTY_PAGE = { page: 0, totalPages: 0, totalElements: 0, hasNext: false };

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '예약 정보를 불러오지 못했습니다.',
});

export const useReservationStore = create((set, get) => ({
  reservations: [],
  pageInfo: EMPTY_PAGE,
  listStatus: 'idle',
  listError: null,
  listRequestId: 0,
  detail: null,
  detailStatus: 'idle',
  detailError: null,
  detailRequestId: 0,

  // 필터를 빠르게 바꾸면 요청이 겹치므로 마지막 요청의 응답만 반영한다.
  loadReservations: async (scope, filters) => {
    const requestId = get().listRequestId + 1;
    set({ listRequestId: requestId, listStatus: 'loading', listError: null });
    try {
      const result = await API_BY_SCOPE[scope].list(filters);
      if (get().listRequestId === requestId) {
        const { content, ...pageInfo } = result;
        set({ reservations: content, pageInfo, listStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().listRequestId === requestId) {
        set({
          reservations: [],
          pageInfo: EMPTY_PAGE,
          listStatus: 'failed',
          listError: toError(error),
        });
      }
    }
  },

  loadReservation: async (scope, reservationId) => {
    const requestId = get().detailRequestId + 1;
    set({ detailRequestId: requestId, detailStatus: 'loading', detailError: null });
    try {
      const detail = await API_BY_SCOPE[scope].detail(reservationId);
      if (get().detailRequestId === requestId) {
        set({ detail, detailStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().detailRequestId === requestId) {
        set({ detail: null, detailStatus: 'failed', detailError: toError(error) });
      }
    }
  },

  clearDetail: () =>
    set((state) => ({
      detail: null,
      detailStatus: 'idle',
      detailError: null,
      detailRequestId: state.detailRequestId + 1,
    })),
}));
