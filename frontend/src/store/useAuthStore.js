import { create } from 'zustand';

/**
 * 인증 상태 store.
 *
 * 로그인 API 가 정해지면 컨벤션 4.2 의 비동기 액션 패턴(status/error)으로 login 액션을 추가한다.
 * 토큰 저장 위치(메모리/브라우저 저장소)와 유지 방식은 공통 인증 구현에서 정하며,
 * 현재는 새로고침 시 초기화되는 메모리 저장만 사용한다.
 */
export const useAuthStore = create((set) => ({
  data: null, // 로그인 사용자 정보
  token: null,
  status: 'idle', // 'idle' | 'loading' | 'succeeded' | 'failed'
  error: null,

  setSession: ({ token, user }) => set({ token, data: user, status: 'succeeded', error: null }),
  clearSession: () => set({ token: null, data: null, status: 'idle', error: null }),
}));
