import { create } from 'zustand';
import { treatmentApi } from '@/api/treatmentApi';
import { onSessionChange } from '@/utils/sessionChange';

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '시술 메뉴 요청에 실패했습니다.',
});

export const useTreatmentStore = create((set, get) => ({
  shopId: null,
  treatments: [],
  loadRequestId: 0,
  status: 'idle',
  mutationStatus: 'idle',
  error: null,
  mutationError: null,
  activeTreatments: [],
  saveImage: async (treatmentId, file) => {
    set({ mutationStatus: 'loading', mutationError: null });
    try {
      const result = file
        ? await treatmentApi.uploadImage(treatmentId, file)
        : await treatmentApi.deleteImage(treatmentId);
      const update = (items) =>
        items.map((item) =>
          item.treatmentId === treatmentId ? { ...item, imageUrl: result?.imageUrl ?? null } : item,
        );
      set((state) => ({
        treatments: update(state.treatments),
        activeTreatments: update(state.activeTreatments),
        mutationStatus: 'succeeded',
      }));
    } catch (error) {
      const failure = toError(error);
      set({ mutationStatus: 'failed', mutationError: failure });
      throw failure;
    }
  },
  selectionStatus: 'idle',
  selectionError: null,
  selectionRequestId: 0,

  // 마지막 요청의 응답만 반영해 늦게 온 이전 계정의 시술이 들어오지 않게 한다
  loadActiveTreatments: async () => {
    const requestId = get().selectionRequestId + 1;
    const isCurrent = () => get().selectionRequestId === requestId;
    set({
      selectionRequestId: requestId,
      selectionStatus: 'loading',
      selectionError: null,
      activeTreatments: [],
    });
    try {
      const user = await treatmentApi.getCurrentUser();
      if (!isCurrent()) return [];
      if (user?.role !== 'OWNER' || !user.shopId) {
        throw { code: 'COMMON_403', message: '매장 계정으로 로그인해 주세요.' };
      }
      const activeTreatments = await treatmentApi.getActiveTreatments(user.shopId);
      if (!isCurrent()) return [];
      set({ activeTreatments, selectionStatus: 'succeeded' });
      return activeTreatments;
    } catch (error) {
      const failure = toError(error);
      if (isCurrent()) set({ selectionStatus: 'failed', selectionError: failure });
      throw failure;
    }
  },

  // 다시 받는 동안 받아 둔 목록을 보여 준다. 계정이 바뀌면 아래 onSessionChange 가 비운다.
  // 마지막 요청의 응답만 반영해 늦게 온 응답이 새 목록을 덮지 않게 한다
  loadTreatments: async () => {
    const requestId = get().loadRequestId + 1;
    const isCurrent = () => get().loadRequestId === requestId;
    set({ loadRequestId: requestId, status: 'loading', error: null, mutationError: null });
    try {
      // 매장 ID·역할은 인증 공통 API에서 받아 사용한다.
      const user = await treatmentApi.getCurrentUser();
      if (!isCurrent()) return;
      if (user?.role !== 'OWNER' || !user.shopId) {
        throw { code: 'COMMON_403', message: '매장 계정으로 로그인해 주세요.' };
      }
      const treatments = await treatmentApi.getTreatments(user.shopId);
      if (isCurrent()) set({ shopId: user.shopId, treatments, status: 'succeeded' });
    } catch (error) {
      if (isCurrent()) set({ status: 'failed', error: toError(error) });
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

  // 서버는 예약 이력 보존을 위해 소프트 삭제하므로 목록에서만 뺀다
  deleteTreatment: async (treatmentId) => {
    set({ mutationStatus: 'loading', mutationError: null });
    try {
      await treatmentApi.deleteTreatment(get().shopId, treatmentId);
      set((state) => ({
        treatments: state.treatments.filter((item) => item.treatmentId !== treatmentId),
        activeTreatments: state.activeTreatments.filter((item) => item.treatmentId !== treatmentId),
        mutationStatus: 'succeeded',
      }));
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
        activeTreatments:
          treatment.status === 'ACTIVE'
            ? [
                treatment,
                ...state.activeTreatments.filter((item) => item.treatmentId !== treatmentId),
              ]
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

onSessionChange(() =>
  useTreatmentStore.setState((state) => ({
    shopId: null,
    treatments: [],
    activeTreatments: [],
    status: 'idle',
    selectionStatus: 'idle',
    error: null,
    selectionError: null,
    loadRequestId: state.loadRequestId + 1,
    selectionRequestId: state.selectionRequestId + 1,
  })),
);
