import { create } from 'zustand';
import { ownerDashboardApi } from '@/api/ownerDashboardApi';

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '통계를 불러오지 못했습니다.',
});

/** 조회 조건을 비교할 수 있는 문자열로. 전체 기간은 날짜와 무관하게 같은 키다 */
export const statsPeriodKey = (period) => (period.all ? 'all' : `${period.from}~${period.to}`);

/**
 * 매장 매출·예약 분석 (GET /owner/dashboard/stats).
 * statsKey 는 지금 들고 있는 stats 가 어떤 조회 조건의 결과인지 나타낸다.
 */
export const useOwnerStatsStore = create((set, get) => ({
  stats: null,
  statsKey: null,
  status: 'idle',
  error: null,
  requestId: 0,

  // 기간을 빠르게 바꾸면 요청이 겹치므로 마지막 요청의 응답만 반영한다.
  // 시작할 때 이전 통계를 비워 다른 기간·다른 매장의 숫자가 남아 보이지 않게 한다
  loadStats: async (period) => {
    const requestId = get().requestId + 1;
    const key = statsPeriodKey(period);
    set({ requestId, stats: null, statsKey: key, status: 'loading', error: null });
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
