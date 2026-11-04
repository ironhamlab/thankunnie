import { useState } from 'react';
import { Link, Outlet } from 'react-router-dom';
import { USER_ROLE } from '@/constants/userRole';
import { useCurrentRole } from '@/hooks/useCurrentRole';
import { useLogout } from '@/hooks/useLogout';
import { useAuthStore } from '@/store/useAuthStore';
import { getHomePathByRole } from '@/utils/authRedirect';
import { getDevTestUser } from '@/utils/devTestUser';
import AuthCard from '@/components/auth/AuthCard';
import Button from '@/components/common/Button';
import styles from './RoleRoute.module.css';

const ROLE_LABEL = {
  [USER_ROLE.CUSTOMER]: '고객',
  [USER_ROLE.OWNER]: '매장',
  [USER_ROLE.ADMIN]: '관리자',
};

const HOME_LABEL = {
  [USER_ROLE.OWNER]: '매장 센터로',
  [USER_ROLE.ADMIN]: '관리자 화면으로',
};

/**
 * 비로그인 전용 화면(로그인·고객 가입·매장 가입) 가드. 이미 로그인했으면 화면 대신 안내를 보여준다.
 * 로그아웃하면 같은 주소에 남아 원래 화면(입력 폼)이 열린다.
 * 개발 서버의 테스트 계정 스위치도 로그인으로 본다 (PrivateRoute 와 같은 기준).
 *
 * 이 화면에서 로그인·가입을 진행해 로그인 상태가 된 경우는 안내를 띄우지 않는다.
 * 각 화면이 성공 직후 다음 화면으로 보내므로, 그 사이에 안내가 깜빡이지 않게 하기 위해서다.
 * 사용: <Route element={<GuestRoute />}> ...로그인·가입 라우트... </Route>
 */
export default function GuestRoute() {
  const token = useAuthStore((state) => state.token);
  const status = useAuthStore((state) => state.status);
  const role = useCurrentRole();
  const logout = useLogout();
  const [hasSubmitted, setHasSubmitted] = useState(false);

  if (status === 'loading' && !hasSubmitted) {
    setHasSubmitted(true);
  }

  const isLoggedIn = Boolean(token) || Boolean(getDevTestUser());
  if (!isLoggedIn || hasSubmitted) {
    return <Outlet />;
  }

  return (
    <AuthCard
      title="이미 로그인되어 있어요"
      description={`${ROLE_LABEL[role] ?? ''} 계정으로 로그인된 상태예요. 다른 계정으로 로그인하거나 새로 가입하려면 먼저 로그아웃해 주세요.`}
    >
      <div className={styles.actions}>
        <Button type="button" variant="soft" onClick={() => logout()}>
          로그아웃
        </Button>
        <Button as={Link} variant="primary" to={getHomePathByRole(role)} replace>
          {HOME_LABEL[role] ?? '홈으로'}
        </Button>
      </div>
    </AuthCard>
  );
}
