import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { getDevTestUser, setDevTestUser } from '@/utils/devTestUser';

/**
 * 사용자가 직접 누른 로그아웃. 로그인 세션과 함께 개발용 테스트 계정 선택도 비운다.
 * 테스트 계정이 남아 있으면 로그아웃 후에도 그 역할로 판단되기 때문이다.
 *
 * 401 자동 세션 해제(main.jsx 의 onUnauthorized → clearSession)는 이 훅을 쓰지 않는다.
 * 쓰면 개발 중 401 한 번에 테스트 계정 스위치가 풀린다.
 *
 * @returns {(to?: string) => void} 로그아웃 후 이동할 주소. 생략하면 현재 화면에 남는다
 */
export function useLogout() {
  const navigate = useNavigate();
  const token = useAuthStore((state) => state.token);
  const clearSession = useAuthStore((state) => state.clearSession);

  return (to) => {
    // 테스트 계정 스위치는 화면을 다시 불러와야 반영된다 (스위치를 바꿀 때와 같은 방식)
    const hadDevTestUser = Boolean(getDevTestUser());
    if (hadDevTestUser) {
      setDevTestUser('');
    }
    if (token) {
      clearSession();
    }
    if (!hadDevTestUser) {
      if (to) {
        navigate(to);
      }
      return;
    }
    if (to) {
      window.location.assign(to);
    } else {
      window.location.reload();
    }
  };
}
