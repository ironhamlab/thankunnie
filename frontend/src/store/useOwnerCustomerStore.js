import { create } from 'zustand';
import { ownerCustomerApi } from '@/api/ownerCustomerApi';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

/**
 * 사장님 고객 관리. 목록·상세·메모 저장을 각각 다른 상태로 둔다.
 * 목록·상세는 마지막 요청의 응답만 반영하고, 시작할 때 이전 값을 비워 다른 고객·다른 사장님의 정보가 남지 않게 한다.
 */
export const useOwnerCustomerStore = create((set, get) => ({
  customers: [],
  pageInfo: { totalElements: 0, totalPages: 0, hasNext: false },
  listStatus: 'idle',
  listError: null,
  listRequestId: 0,

  detail: null,
  detailUserId: null,
  detailStatus: 'idle',
  detailError: null,
  detailRequestId: 0,

  memoStatus: 'idle',
  memoError: null,

  loadCustomers: async (query) => {
    const requestId = get().listRequestId + 1;
    set({ listRequestId: requestId, customers: [], listStatus: 'loading', listError: null });
    try {
      const result = await ownerCustomerApi.getCustomers(query);
      if (get().listRequestId === requestId) {
        set({
          customers: result?.content ?? [],
          pageInfo: {
            totalElements: result?.totalElements ?? 0,
            totalPages: result?.totalPages ?? 0,
            hasNext: result?.hasNext ?? false,
          },
          listStatus: 'succeeded',
        });
      }
    } catch (error) {
      if (get().listRequestId === requestId) {
        set({
          listStatus: 'failed',
          listError: toError(error, '고객 목록을 불러오지 못했습니다.'),
        });
      }
    }
  },

  loadCustomer: async (userId) => {
    const requestId = get().detailRequestId + 1;
    set({
      detailRequestId: requestId,
      detail: null,
      detailUserId: userId,
      detailStatus: 'loading',
      detailError: null,
      memoStatus: 'idle',
      memoError: null,
    });
    try {
      const detail = await ownerCustomerApi.getCustomer(userId);
      if (get().detailRequestId === requestId) {
        set({ detail, detailStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().detailRequestId === requestId) {
        set({
          detailStatus: 'failed',
          detailError: toError(error, '고객 정보를 불러오지 못했습니다.'),
        });
      }
    }
  },

  clearCustomer: () => {
    set((state) => ({
      detailRequestId: state.detailRequestId + 1,
      detail: null,
      detailUserId: null,
      detailStatus: 'idle',
      detailError: null,
      memoStatus: 'idle',
      memoError: null,
    }));
  },

  // 저장 중에는 다시 저장하지 않는다. 응답이 왔을 때 보고 있는 고객이 바뀌었으면 화면에 반영하지 않는다.
  // 목록의 메모 표시도 함께 맞춘다
  saveMemo: async (userId, memo) => {
    if (get().memoStatus === 'saving') return false;
    const detailRequestId = get().detailRequestId;
    const isCurrent = () =>
      get().detailRequestId === detailRequestId && get().detailUserId === userId;
    set({ memoStatus: 'saving', memoError: null });
    try {
      const saved = await ownerCustomerApi.saveMemo(userId, memo);
      set((state) => ({
        customers: state.customers.map((customer) =>
          customer.userId === userId ? { ...customer, hasMemo: Boolean(saved) } : customer,
        ),
      }));
      if (isCurrent()) {
        set((state) => ({
          memoStatus: 'succeeded',
          detail: state.detail ? { ...state.detail, memo: saved ?? null } : state.detail,
        }));
      }
      return true;
    } catch (error) {
      if (isCurrent()) {
        set({ memoStatus: 'failed', memoError: toError(error, '메모를 저장하지 못했습니다.') });
      }
      return false;
    }
  },
}));
