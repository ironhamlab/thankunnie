import { create } from 'zustand';
import { cancelApi } from '@/api/cancelApi';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

/**
 * 예약 취소 (E-F1~F3). 견적·실행 요청 상태와 마지막 취소 결과를 둔다.
 * 실행 실패는 화면이 오류 코드별로 다르게 안내해야 하므로 호출한 쪽에 그대로 던진다.
 */
export const useCancelStore = create((set, get) => ({
  quote: null,
  quoteStatus: 'idle',
  quoteError: null,
  quoteRequestId: 0,
  submitting: false,
  // 결과 화면이 서버 재조회 전에 바로 보여줄 직전 실행 결과. 새로고침하면 사라지므로 서버 상세가 기준이다
  lastResult: null,

  loadQuote: async (reservationId) => {
    const requestId = get().quoteRequestId + 1;
    set({ quoteRequestId: requestId, quoteStatus: 'loading', quoteError: null });
    try {
      const quote = await cancelApi.getCancelQuote(reservationId);
      if (get().quoteRequestId === requestId) {
        set({ quote, quoteStatus: 'succeeded' });
      }
      return quote;
    } catch (error) {
      if (get().quoteRequestId === requestId) {
        set({
          quote: null,
          quoteStatus: 'failed',
          quoteError: toError(error, '환불 견적을 불러오지 못했습니다.'),
        });
      }
      return null;
    }
  },

  cancelByCustomer: async (reservationId, expectedRefundAmount) => {
    set({ submitting: true });
    try {
      const result = await cancelApi.cancelByCustomer(reservationId, expectedRefundAmount);
      set({ lastResult: result });
      return result;
    } catch (error) {
      throw toError(error, '예약을 취소하지 못했습니다.');
    } finally {
      set({ submitting: false });
    }
  },

  clearQuote: () =>
    set((state) => ({
      quote: null,
      quoteStatus: 'idle',
      quoteError: null,
      quoteRequestId: state.quoteRequestId + 1,
    })),
}));
