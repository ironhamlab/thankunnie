import { create } from 'zustand';
import { userApi } from '@/api/userApi';

const toError = (error, fallback) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? fallback,
});

/** 내 회원 정보. 마이페이지 상단이 쓴다. */
export const useUserStore = create((set, get) => ({
  profile: null,
  profileStatus: 'idle',
  profileError: null,
  profileRequestId: 0,

  // 테스트 계정 전환 등으로 겹친 조회는 마지막 요청의 응답만 반영한다
  // 다른 계정의 개인정보가 남아 보이지 않도록 조회를 시작할 때 이전 값을 지운다
  loadProfile: async () => {
    const requestId = get().profileRequestId + 1;
    set({
      profile: null,
      profileRequestId: requestId,
      profileStatus: 'loading',
      profileError: null,
    });
    try {
      const profile = await userApi.getMyProfile();
      if (get().profileRequestId === requestId) {
        set({ profile, profileStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().profileRequestId !== requestId) {
        return;
      }
      set({
        profile: null,
        profileStatus: 'failed',
        profileError: toError(error, '회원 정보를 불러오지 못했습니다.'),
      });
    }
  },

  // 진행 중인 조회의 응답도 버리도록 요청 번호를 올린다
  resetProfile: () =>
    set((state) => ({
      profile: null,
      profileStatus: 'idle',
      profileError: null,
      profileRequestId: state.profileRequestId + 1,
    })),
}));
