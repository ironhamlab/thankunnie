import { create } from 'zustand';
import { notificationApi } from '@/api/notificationApi';

/**
 * 헤더 종 알림. 안 읽은 개수는 스트림으로 받고, 목록은 종을 열 때와 모두 읽음 뒤에 가져온다.
 */
export const useInboxStore = create((set) => ({
  unreadCount: 0,
  items: [],
  listStatus: 'idle',
  listError: null,
  toasts: [],

  applyUnreadCount: (count) => {
    set({ unreadCount: count });
  },

  enqueueToasts: (items) => {
    set((state) => {
      const shown = new Set(state.toasts.map((toast) => toast.inboxId));
      const added = items.filter((item) => item?.inboxId != null && !shown.has(item.inboxId));
      if (added.length === 0) {
        return state;
      }
      return { toasts: [...state.toasts, ...added].slice(-3) };
    });
  },

  dismissToast: (inboxId) => {
    set((state) => ({
      toasts: state.toasts.filter((toast) => toast.inboxId !== inboxId),
    }));
  },

  loadInbox: async () => {
    set({ listStatus: 'loading', listError: null });
    try {
      const items = await notificationApi.getInbox();
      set({ items: items ?? [], listStatus: 'succeeded' });
    } catch (error) {
      set({
        listStatus: 'failed',
        listError: error?.message ?? '알림을 불러오지 못했습니다.',
      });
    }
  },

  markAllRead: async () => {
    const count = await notificationApi.markAllInboxRead();
    set({ unreadCount: count ?? 0 });
  },

  clear: () => {
    set({ unreadCount: 0, items: [], listStatus: 'idle', listError: null, toasts: [] });
  },
}));
