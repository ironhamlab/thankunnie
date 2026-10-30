import { create } from 'zustand';
import { ownerDashboardApi } from '@/api/ownerDashboardApi';
import { reservationApi } from '@/api/reservationApi';
import { todayKst } from '@/utils/scheduleDate';

// 오늘 일정에는 시간을 잡고 있는 확정·취소 처리 중 예약만 보여 준다
const TODAY_STATUSES = ['CONFIRMED', 'CANCEL_PENDING'];
const TODAY_PAGE_SIZE = 50;

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '운영 현황을 불러오지 못했습니다.',
});

/**
 * 사장님 메인 대시보드. 지표(GET /owner/dashboard)와 오늘 예약 목록(GET /owner/reservations)을 함께 불러온다.
 * 예약 현황 화면의 목록 상태와 섞이지 않도록 예약 스토어와 따로 둔다.
 */
export const useOwnerDashboardStore = create((set, get) => ({
  dashboard: null,
  todayReservations: [],
  status: 'idle',
  error: null,
  requestId: 0,

  loadDashboard: async () => {
    const requestId = get().requestId + 1;
    set({ requestId, status: 'loading', error: null });
    const today = todayKst();
    try {
      const [dashboard, reservations] = await Promise.all([
        ownerDashboardApi.getDashboard(),
        reservationApi.getOwnerReservations({
          from: today,
          to: today,
          statuses: TODAY_STATUSES,
          size: TODAY_PAGE_SIZE,
        }),
      ]);
      if (get().requestId === requestId) {
        set({
          dashboard,
          todayReservations: reservations?.content ?? [],
          status: 'succeeded',
        });
      }
    } catch (error) {
      if (get().requestId === requestId) {
        set({ status: 'failed', error: toError(error) });
      }
    }
  },
}));
