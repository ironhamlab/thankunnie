import { create } from 'zustand';
import { notificationApi } from '@/api/notificationApi';

/**
 * 헤더 종 알림. 안 읽은 개수는 스트림으로 받고, 목록은 종을 열 때와 모두 읽음 뒤에 가져온다.
 * clear()가 세대를 올리면, 그 전에 시작한 목록 조회 응답은 버린다.
 */
let inboxRequestGeneration = 0;

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
    const requestGeneration = inboxRequestGeneration;
    set({ listStatus: 'loading', listError: null });
    try {
      const items = await notificationApi.getInbox();
      if (requestGeneration !== inboxRequestGeneration) {
        return;
      }
      set({ items: items ?? [], listStatus: 'succeeded' });
    } catch (error) {
      if (requestGeneration !== inboxRequestGeneration) {
        return;
      }
      set({
        listStatus: 'failed',
        listError: error?.message ?? '알림을 불러오지 못했습니다.',
      });
    }
  },

  markRead: async (inboxId) => {
    const count = await notificationApi.markInboxRead(inboxId);
    set((state) => ({
      unreadCount: count ?? 0,
      items: state.items.map((item) =>
        item.inboxId === inboxId && item.readAt == null
          ? { ...item, readAt: new Date().toISOString() }
          : item,
      ),
    }));
  },

  markAllRead: async () => {
    const count = await notificationApi.markAllInboxRead();
    set({ unreadCount: count ?? 0 });
  },

  clear: () => {
    inboxRequestGeneration += 1;
    set({ unreadCount: 0, items: [], listStatus: 'idle', listError: null, toasts: [] });
  },
}));
