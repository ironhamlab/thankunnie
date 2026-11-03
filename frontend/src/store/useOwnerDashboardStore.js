import { create } from 'zustand';
import { ownerDashboardApi } from '@/api/ownerDashboardApi';
import { reservationApi } from '@/api/reservationApi';
import { todayKst } from '@/utils/scheduleDate';

// 오늘 일정에는 시간을 잡고 있는 확정·취소 처리 중 예약만 보여 준다
const TODAY_STATUSES = ['CONFIRMED', 'CANCEL_PENDING'];
const TODAY_PAGE_SIZE = 50;

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

/**
 * 매장 메인 대시보드. 오늘 지표(GET /owner/dashboard)와 오늘 예약 목록(GET /owner/reservations)은
 * 서로 독립된 상태로 둔다. 한쪽이 실패해도 다른 쪽은 그대로 보여 주고, 실패한 영역만 다시 불러온다.
 * 예약 현황 화면의 목록 상태와 섞이지 않도록 예약 스토어와 따로 둔다.
 */
export const useOwnerDashboardStore = create((set, get) => ({
  dashboard: null,
  dashboardStatus: 'idle',
  dashboardError: null,
  dashboardRequestId: 0,
  todayReservations: [],
  reservationsStatus: 'idle',
  reservationsError: null,
  reservationsRequestId: 0,

  loadDashboard: () => {
    get().loadToday();
    get().loadTodayReservations();
  },

  // 요청 시작 시 이전 값을 비운다. 로그아웃 후 다른 매장이 로그인해도 이전 매장 숫자가 남지 않게 한다
  loadToday: async () => {
    const requestId = get().dashboardRequestId + 1;
    set({
      dashboardRequestId: requestId,
      dashboard: null,
      dashboardStatus: 'loading',
      dashboardError: null,
    });
    try {
      const dashboard = await ownerDashboardApi.getDashboard();
      if (get().dashboardRequestId === requestId) {
        set({ dashboard, dashboardStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().dashboardRequestId === requestId) {
        set({
          dashboardStatus: 'failed',
          dashboardError: toError(error, '운영 현황을 불러오지 못했습니다.'),
        });
      }
    }
  },

  loadTodayReservations: async () => {
    const requestId = get().reservationsRequestId + 1;
    set({
      reservationsRequestId: requestId,
      todayReservations: [],
      reservationsStatus: 'loading',
      reservationsError: null,
    });
    const today = todayKst();
    try {
      const result = await reservationApi.getOwnerReservations({
        from: today,
        to: today,
        statuses: TODAY_STATUSES,
        size: TODAY_PAGE_SIZE,
      });
      if (get().reservationsRequestId === requestId) {
        set({ todayReservations: result?.content ?? [], reservationsStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().reservationsRequestId === requestId) {
        set({
          reservationsStatus: 'failed',
          reservationsError: toError(error, '오늘 예약을 불러오지 못했습니다.'),
        });
      }
    }
  },
}));
