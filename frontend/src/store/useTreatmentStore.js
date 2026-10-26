import { create } from 'zustand';
import { treatmentApi } from '@/api/treatmentApi';

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '시술 메뉴 요청에 실패했습니다.',
});

export const useTreatmentStore = create((set, get) => ({
  shopId: null,
  treatments: [],
  categories: [],
  status: 'idle',
  mutationStatus: 'idle',
  error: null,
  mutationError: null,
  activeTreatments: [],
  selectionStatus: 'idle',
  selectionError: null,

  loadActiveTreatments: async () => {
    set({ selectionStatus: 'loading', selectionError: null, activeTreatments: [] });
    try {
      const user = await treatmentApi.getCurrentUser();
      if (user?.role !== 'OWNER' || !user.shopId) {
        throw { code: 'COMMON_403', message: '사장님 계정으로 로그인해 주세요.' };
      }
      const activeTreatments = await treatmentApi.getActiveTreatments(user.shopId);
      set({ activeTreatments, selectionStatus: 'succeeded' });
      return activeTreatments;
    } catch (error) {
      const failure = toError(error);
      set({ selectionStatus: 'failed', selectionError: failure });
      throw failure;
    }
  },

  loadTreatments: async () => {
    set({
      status: 'loading',
      error: null,
      mutationError: null,
      treatments: [],
      categories: [],
      shopId: null,
    });
    try {
      // 매장 ID·역할은 인증 공통 API에서 받아 사용한다.
      const user = await treatmentApi.getCurrentUser();
      if (user?.role !== 'OWNER' || !user.shopId) {
        throw { code: 'COMMON_403', message: '사장님 계정으로 로그인해 주세요.' };
      }
      const [treatments, categories] = await Promise.all([
        treatmentApi.getTreatments(user.shopId),
        treatmentApi.getCategories(user.shopId),
      ]);
      set({ shopId: user.shopId, treatments, categories, status: 'succeeded' });
    } catch (error) {
      set({ status: 'failed', error: toError(error) });
    }
  },

  saveTreatment: async (treatmentId, payload) => {
    set({ mutationStatus: 'loading', mutationError: null });
    try {
      const shopId = get().shopId;
      const treatment = treatmentId
        ? await treatmentApi.updateTreatment(shopId, treatmentId, payload)
        : await treatmentApi.createTreatment(shopId, payload);
      set((state) => ({
        treatments: treatmentId
          ? state.treatments.map((item) => (item.treatmentId === treatmentId ? treatment : item))
          : [treatment, ...state.treatments],
        mutationStatus: 'succeeded',
      }));
      return treatment;
    } catch (error) {
      const failure = toError(error);
      set({ mutationStatus: 'failed', mutationError: failure });
      throw failure;
    }
  },

  changeTreatmentStatus: async (treatmentId, status) => {
    set({ mutationStatus: 'loading', mutationError: null });
    try {
      const treatment = await treatmentApi.updateStatus(get().shopId, treatmentId, status);
      set((state) => ({
        activeTreatments: treatment.status === 'ACTIVE'
          ? [treatment, ...state.activeTreatments.filter((item) => item.treatmentId !== treatmentId)]
          : state.activeTreatments.filter((item) => item.treatmentId !== treatmentId),
        treatments: state.treatments.map((item) =>
          item.treatmentId === treatmentId ? treatment : item,
        ),
        mutationStatus: 'succeeded',
      }));
      return treatment;
    } catch (error) {
      const failure = toError(error);
      set({ mutationStatus: 'failed', mutationError: failure });
      throw failure;
    }
  },
}));
