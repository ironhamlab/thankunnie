import { create } from 'zustand';
import { shopDetailApi } from '@/api/shopDetailApi';

const toError = (error, message) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? message,
  status: error?.status,
});

export const useShopDetailStore = create((set, get) => ({
  shop: null,
  status: 'idle',
  error: null,
  requestId: 0,
  // 선택한 날짜·소요 시간의 시작 가능 시각. key 로 지금 화면의 선택과 같은 응답인지 확인한다
  times: { key: null, status: 'idle', startTimes: [], error: null },
  timesRequestId: 0,
  loadShop: async (shopId) => {
    const requestId = get().requestId + 1;
    set({ requestId, shop: null, status: 'loading', error: null });
    try {
      const shop = await shopDetailApi.getShop(shopId);
      if (!shop) throw { code: 'EMPTY_RESPONSE', message: '매장 정보를 찾을 수 없습니다.' };
      // 다른 매장으로 이동한 뒤 도착한 이전 응답은 표시하지 않는다.
      if (get().requestId === requestId) set({ shop, status: 'succeeded' });
    } catch (error) {
      if (get().requestId === requestId) {
        set({
          shop: null,
          status: 'failed',
          error: toError(error, '매장 정보를 불러오지 못했습니다.'),
        });
      }
    }
  },
  loadAvailableTimes: async (shopId, date, durationMinutes) => {
    const timesRequestId = get().timesRequestId + 1;
    const key = `${shopId}-${date}-${durationMinutes}`;
    set({
      timesRequestId,
      times: { key, status: 'loading', startTimes: [], heldStartTimes: [], error: null },
    });
    try {
      const response = await shopDetailApi.getAvailableTimes(shopId, date, durationMinutes);
      if (get().timesRequestId === timesRequestId) {
        set({
          times: {
            key,
            status: 'succeeded',
            startTimes: response?.startTimes ?? [],
            // 영업시간 밖 연장 영업(매장이 연 시간) 덕분에 가능한 시각. 화면에 연장 표시만 한다
            extendedStartTimes: response?.extendedStartTimes ?? [],
            // 다른 고객이 결제 중이라 지금은 못 잡지만 대기열에 설 수 있는 시각
            heldStartTimes: response?.heldStartTimes ?? [],
            error: null,
          },
        });
      }
    } catch (error) {
      if (get().timesRequestId === timesRequestId) {
        set({
          times: {
            key,
            status: 'failed',
            startTimes: [],
            heldStartTimes: [],
            error: toError(error, '예약 가능 시간을 불러오지 못했습니다.'),
          },
        });
      }
    }
  },
  // 고른 날짜의 시각을 화면을 보는 동안 다시 받는다. 받아 둔 칸을 그대로 두고, 바뀐 내용이 있을 때만 교체한다.
  // 실패해도 다음 주기에 다시 받으므로 받아 둔 칸을 지우지 않는다
  refreshAvailableTimes: async (shopId, date, durationMinutes) => {
    const key = `${shopId}-${date}-${durationMinutes}`;
    const { times, timesRequestId: lastRequestId } = get();
    if (times.key !== key || times.status !== 'succeeded') return;
    const timesRequestId = lastRequestId + 1;
    set({ timesRequestId });
    try {
      const response = await shopDetailApi.getAvailableTimes(shopId, date, durationMinutes);
      if (get().timesRequestId !== timesRequestId) return;
      const next = {
        key,
        status: 'succeeded',
        startTimes: response?.startTimes ?? [],
        extendedStartTimes: response?.extendedStartTimes ?? [],
        heldStartTimes: response?.heldStartTimes ?? [],
        error: null,
      };
      if (JSON.stringify(get().times) !== JSON.stringify(next)) set({ times: next });
    } catch {
      // 다음 주기에 다시 받는다
    }
  },
  clearShop: () =>
    set((state) => ({
      shop: null,
      status: 'idle',
      error: null,
      requestId: state.requestId + 1,
      times: { key: null, status: 'idle', startTimes: [], error: null },
      timesRequestId: state.timesRequestId + 1,
    })),
}));
