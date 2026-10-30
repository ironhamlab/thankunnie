import { create } from 'zustand';
import { ownerDashboardApi } from '@/api/ownerDashboardApi';

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '통계를 불러오지 못했습니다.',
});

/**
 * 사장님 기간 통계 (GET /owner/dashboard/stats).
 */
export const useOwnerStatsStore = create((set, get) => ({
  stats: null,
  status: 'idle',
  error: null,
  requestId: 0,

  // 기간을 빠르게 바꾸면 요청이 겹치므로 마지막 요청의 응답만 반영한다
  loadStats: async (period) => {
    const requestId = get().requestId + 1;
    set({ requestId, status: 'loading', error: null });
    try {
      const stats = await ownerDashboardApi.getStats(period);
      if (get().requestId === requestId) {
        set({ stats, status: 'succeeded' });
      }
    } catch (error) {
      if (get().requestId === requestId) {
        set({ status: 'failed', error: toError(error) });
      }
    }
  },
}));
