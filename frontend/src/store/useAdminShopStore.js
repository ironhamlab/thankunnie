import { create } from 'zustand';
import { adminApi } from '@/api/adminApi';

const failure = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '매장 가입 신청 요청에 실패했습니다.',
});

// 탭을 빠르게 바꿔도 마지막 요청의 응답만 반영한다
let loadSeq = 0;

export const useAdminShopStore = create((set) => {
  // 승인·거절·되돌리기는 저장 중 표시와 실패 처리가 같다
  const save = async (request) => {
    set({ isSaving: true });
    try {
      return await request();
    } catch (error) {
      throw failure(error);
    } finally {
      set({ isSaving: false });
    }
  };

  return {
    applications: [],
    pageInfo: { totalElements: 0, totalPages: 0, hasNext: false },
    listStatus: 'idle',
    listError: null,
    isSaving: false,

    loadApplications: async (params) => {
      const current = ++loadSeq;
      set({ listStatus: 'loading', listError: null });
      try {
        const { content, ...pageInfo } = await adminApi.getShopApplications(params);
        if (current === loadSeq) set({ applications: content, pageInfo, listStatus: 'succeeded' });
      } catch (error) {
        if (current === loadSeq) {
          set({ applications: [], listStatus: 'failed', listError: failure(error) });
        }
      }
    },

    approve: (shopId) => save(() => adminApi.approveShopApplication(shopId)),
    reject: (shopId, reason) => save(() => adminApi.rejectShopApplication(shopId, reason)),
    cancelApproval: (shopId, reason) => save(() => adminApi.cancelShopApproval(shopId, reason)),
    cancelRejection: (shopId) => save(() => adminApi.cancelShopRejection(shopId)),
  };
});
