import { useAuthStore } from '@/store/useAuthStore';
import { getDevTestUserRole } from '@/utils/devTestUser';

/**
 * 현재 사용자 역할. 로그인 세션이 있으면 그 역할, 없으면 개발용 테스트 계정 스위치의 역할,
 * 둘 다 없으면 null(비로그인). main.jsx 의 getToken 과 같은 우선순위다.
 */
export function useCurrentRole() {
  const role = useAuthStore((state) => state.data?.role);
  return role ?? getDevTestUserRole();
}
