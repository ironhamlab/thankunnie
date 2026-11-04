import { create } from 'zustand';
import { ownerCustomerApi } from '@/api/ownerCustomerApi';
import { onSessionChange } from '@/utils/sessionChange';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

/**
 * 매장 고객 관리. 목록·상세·메모 저장을 각각 다른 상태로 둔다.
 * 목록·상세는 마지막 요청의 응답만 반영하고, 시작할 때 이전 값을 비워 다른 고객·다른 매장의 정보가 남지 않게 한다.
 */
export const useOwnerCustomerStore = create((set, get) => ({
  customers: [],
  pageInfo: { totalElements: 0, totalPages: 0, hasNext: false },
  listStatus: 'idle',
  listError: null,
  listRequestId: 0,
  listKey: null,

  detail: null,
  detailUserId: null,
  detailStatus: 'idle',
  detailError: null,
  detailRequestId: 0,

  memoStatus: 'idle',
  memoError: null,
  // 저장 요청이 진행 중인 고객 ID. 다른 고객을 보다 돌아와도 같은 고객의 저장이 겹치지 않게 한다
  memoSavingUserIds: [],
  // 상세를 불러오는 동안 끝난 저장. 그 상세 응답은 저장 전 메모일 수 있어 이 값으로 덮는다
  savedMemoDuringLoad: null,

  loadCustomers: async (query) => {
    const requestId = get().listRequestId + 1;
    // 같은 조건이면 다시 받는 동안 받아 둔 목록을 보여 준다. 조건이 바뀌면 비운다
    const listKey = JSON.stringify(query);
    set({
      listRequestId: requestId,
      listKey,
      listStatus: 'loading',
      listError: null,
      ...(get().listKey === listKey ? {} : { customers: [] }),
    });
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
      memoStatus: get().memoSavingUserIds.includes(userId) ? 'saving' : 'idle',
      memoError: null,
      savedMemoDuringLoad: null,
    });
    try {
      const detail = await ownerCustomerApi.getCustomer(userId);
      if (get().detailRequestId === requestId) {
        const savedDuringLoad = get().savedMemoDuringLoad;
        set({
          detail:
            savedDuringLoad?.requestId === requestId
              ? { ...detail, memo: savedDuringLoad.memo }
              : detail,
          detailStatus: 'succeeded',
          savedMemoDuringLoad: null,
        });
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
      savedMemoDuringLoad: null,
      detailRequestId: state.detailRequestId + 1,
      detail: null,
      detailUserId: null,
      detailStatus: 'idle',
      detailError: null,
      memoStatus: 'idle',
      memoError: null,
    }));
  },

  // 같은 고객의 저장이 진행 중이면 다시 저장하지 않는다. 고객별로 한 번에 하나만 보내므로 응답 순서가 뒤바뀌지 않는다.
  // 응답이 왔을 때 그 고객을 보고 있을 때만 화면에 반영하고, 목록의 메모 표시는 항상 맞춘다
  saveMemo: async (userId, memo) => {
    if (get().memoSavingUserIds.includes(userId)) return false;
    const isCurrent = () => get().detailUserId === userId;
    const finish = (state) => state.memoSavingUserIds.filter((id) => id !== userId);
    set((state) => ({
      memoSavingUserIds: [...state.memoSavingUserIds, userId],
      memoStatus: 'saving',
      memoError: null,
    }));
    try {
      const saved = await ownerCustomerApi.saveMemo(userId, memo);
      set((state) => ({
        memoSavingUserIds: finish(state),
        customers: state.customers.map((customer) =>
          customer.userId === userId ? { ...customer, hasMemo: Boolean(saved) } : customer,
        ),
      }));
      if (isCurrent()) {
        set((state) => ({
          memoStatus: 'succeeded',
          detail: state.detail ? { ...state.detail, memo: saved ?? null } : state.detail,
          // 상세가 아직 오는 중이면 그 응답에 이 메모를 덮어쓴다
          savedMemoDuringLoad:
            state.detailStatus === 'loading'
              ? { requestId: state.detailRequestId, memo: saved ?? null }
              : state.savedMemoDuringLoad,
        }));
      }
      return true;
    } catch (error) {
      set((state) => ({ memoSavingUserIds: finish(state) }));
      if (isCurrent()) {
        set({ memoStatus: 'failed', memoError: toError(error, '메모를 저장하지 못했습니다.') });
      }
      return false;
    }
  },
}));

onSessionChange(() => {
  useOwnerCustomerStore.setState((state) => ({
    customers: [],
    pageInfo: { totalElements: 0, totalPages: 0, hasNext: false },
    listStatus: 'idle',
    listError: null,
    listKey: null,
    listRequestId: state.listRequestId + 1,
  }));
  useOwnerCustomerStore.getState().clearCustomer();
});
