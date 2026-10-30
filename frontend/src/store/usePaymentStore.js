import { create } from 'zustand';
import { paymentApi } from '@/api/paymentApi';

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '결제를 확인하지 못했습니다.',
});

/**
 * 예약금 결제 확인. 결제 화면(팝업 결제)과 결과 화면(모바일 결제창 복귀) 양쪽에서 부른다.
 * 실패는 화면이 오류 코드별로 다르게 안내해야 하므로 호출한 쪽에 그대로 던진다.
 */
export const usePaymentStore = create((set) => ({
  verifying: false,

  verifyPayment: async (body) => {
    set({ verifying: true });
    try {
      return await paymentApi.verify(body);
    } catch (error) {
      throw toError(error);
    } finally {
      set({ verifying: false });
    }
  },
}));
