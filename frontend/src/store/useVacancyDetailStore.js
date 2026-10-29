import { create } from 'zustand';
import { vacancyDetailApi } from '@/api/vacancyDetailApi';

export const useVacancyDetailStore = create((set, get) => ({
  detail: null,
  status: 'idle',
  error: null,
  requestId: 0,
  loadDetail: async (treatmentId, startsAt) => {
    const requestId = get().requestId + 1;
    set({ requestId, detail: null, status: 'loading', error: null });
    try {
      const detail = await vacancyDetailApi.getDetail(treatmentId, startsAt);
      if (!detail) throw { code: 'EMPTY_RESPONSE', message: '시술 정보를 찾을 수 없습니다.' };
      // 다른 시술·시간으로 이동한 뒤 도착한 이전 응답은 표시하지 않는다.
      if (get().requestId === requestId) set({ detail, status: 'succeeded' });
    } catch (error) {
      if (get().requestId === requestId) {
        set({
          detail: null,
          status: 'failed',
          error: {
            code: error?.code ?? 'UNKNOWN_ERROR',
            message: error?.message ?? '시술 정보를 불러오지 못했습니다.',
            status: error?.status,
          },
        });
      }
    }
  },
  clearDetail: () =>
    set((state) => ({
      detail: null,
      status: 'idle',
      error: null,
      requestId: state.requestId + 1,
    })),
}));
