import { create } from 'zustand';
import { notificationApi } from '@/api/notificationApi';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

/** 고객 빈자리 알림 조건(구독). 마이페이지 알림 조건 탭과 요약 건수가 함께 쓴다. */
export const useNotificationStore = create((set, get) => ({
  subscriptions: [],
  listStatus: 'idle',
  listError: null,
  listRequestId: 0,
  categories: [],
  categoryStatus: 'idle',
  saving: false,

  // 등록·해제보다 늦게 도착한 이전 조회가 목록을 덮지 않도록 마지막 요청의 응답만 반영한다
  loadSubscriptions: async () => {
    const requestId = get().listRequestId + 1;
    set({ listRequestId: requestId, listStatus: 'loading', listError: null });
    try {
      const subscriptions = await notificationApi.getMySubscriptions();
      if (get().listRequestId === requestId) {
        set({ subscriptions: subscriptions ?? [], listStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().listRequestId !== requestId) {
        return;
      }
      set({
        subscriptions: [],
        listStatus: 'failed',
        listError: toError(error, '알림 조건을 불러오지 못했습니다.'),
      });
    }
  },

  // 카테고리는 자주 바뀌지 않으므로 한 번 받은 목록을 재사용한다
  loadCategories: async () => {
    if (get().categoryStatus === 'succeeded' || get().categoryStatus === 'loading') {
      return;
    }
    set({ categoryStatus: 'loading' });
    try {
      const categories = await notificationApi.getCategories();
      set({ categories: categories ?? [], categoryStatus: 'succeeded' });
    } catch {
      set({ categories: [], categoryStatus: 'failed' });
    }
  },

  /** 등록 결과를 { ok, error } 로 돌려준다. 화면이 성공·실패 안내를 직접 띄운다 */
  addSubscription: async (request) => {
    set({ saving: true });
    try {
      const created = await notificationApi.createSubscription(request);
      set((state) => ({ subscriptions: [created, ...state.subscriptions], saving: false }));
      get().reloadIfLoading();
      return { ok: true };
    } catch (error) {
      set({ saving: false });
      return { ok: false, error: toError(error, '알림 조건을 저장하지 못했습니다.') };
    }
  },

  /** 구독 유형은 유지한 채 대상과 희망 시간만 바꾼다 */
  updateSubscription: async (subscriptionId, request) => {
    set({ saving: true });
    try {
      const updated = await notificationApi.updateSubscription(subscriptionId, request);
      set((state) => ({
        subscriptions: state.subscriptions.map((item) =>
          item.subscriptionId === subscriptionId ? updated : item,
        ),
        saving: false,
      }));
      get().reloadIfLoading();
      return { ok: true };
    } catch (error) {
      set({ saving: false });
      return { ok: false, error: toError(error, '알림 조건을 수정하지 못했습니다.') };
    }
  },

  removeSubscription: async (subscriptionId) => {
    set({ saving: true });
    try {
      await notificationApi.deleteSubscription(subscriptionId);
      set((state) => ({
        subscriptions: state.subscriptions.filter((item) => item.subscriptionId !== subscriptionId),
        saving: false,
      }));
      get().reloadIfLoading();
      return { ok: true };
    } catch (error) {
      set({ saving: false });
      return { ok: false, error: toError(error, '알림 조건을 해제하지 못했습니다.') };
    }
  },

  // 변경 전에 보낸 조회가 아직 진행 중이면 그 응답은 버리고 서버 목록을 새로 받는다
  reloadIfLoading: () => {
    if (get().listStatus === 'loading') {
      get().loadSubscriptions();
    }
  },
}));
