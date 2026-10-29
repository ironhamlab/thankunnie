import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { USER_ROLE } from '@/constants/userRole';
import { useCurrentRole } from '@/hooks/useCurrentRole';
import { useAuthStore } from '@/store/useAuthStore';
import { getHomePathByRole } from '@/utils/authRedirect';
import { setDevTestUser } from '@/utils/devTestUser';
import Button from '@/components/common/Button';
import Dialog from '@/components/common/Dialog';
import styles from './RoleRoute.module.css';

const ROLE_LABEL = {
  [USER_ROLE.CUSTOMER]: '고객',
  [USER_ROLE.OWNER]: '사장님',
  [USER_ROLE.ADMIN]: '관리자',
};

/**
 * 화면 영역별 역할 가드. 허용 역할이 아닌 계정으로 들어오면 화면 대신 확인 창을 띄운다.
 * 로그아웃하면 같은 주소에 비로그인으로 남고, 로그인이 필요한 화면이면 PrivateRoute 가 로그인으로 보낸다.
 * 비로그인은 통과시킨다 (로그인 필요 여부는 PrivateRoute 가 판단).
 * 서버도 역할을 검사(403)하므로 이 가드는 화면이 깨지지 않게 하는 용도다.
 *
 * @param {string[]} allow 허용 역할 (constants/userRole)
 * @param {string} areaName 확인 창 문구에 쓸 영역 이름 (예: '고객 화면')
 * @param {string[]} [redirectPaths] 확인 창 없이 역할별 첫 화면으로 바로 보낼 주소 (예: 사장님이 홈에 온 경우)
 */
export default function RoleRoute({ allow, areaName, redirectPaths = [] }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const role = useCurrentRole();
  const token = useAuthStore((state) => state.token);
  const clearSession = useAuthStore((state) => state.clearSession);

  if (!role || allow.includes(role)) {
    return <Outlet />;
  }
  if (redirectPaths.includes(pathname)) {
    return <Navigate to={getHomePathByRole(role)} replace />;
  }

  const handleBack = () => navigate(getHomePathByRole(role), { replace: true });

  const handleLogout = () => {
    if (token) {
      clearSession();
      return;
    }
    // 개발용 테스트 계정은 스위치와 같은 방식으로 비로그인으로 바꾼다
    setDevTestUser('');
    window.location.reload();
  };

  // 레이아웃 밖에서 띄우므로 공통 배경을 깔아 둔다
  return (
    <div className="beauty-app">
      <Dialog title="로그아웃이 필요해요" onClose={handleBack}>
        <p>
          {ROLE_LABEL[role]} 계정으로 로그인되어 있어요. {areaName}을 이용하려면 로그아웃해야
          합니다. 로그아웃할까요?
        </p>
        <div className={styles.actions}>
          <Button type="button" variant="soft" onClick={handleBack}>
            {role === USER_ROLE.OWNER ? '사장님 센터로 돌아가기' : '홈으로 돌아가기'}
          </Button>
          <Button type="button" variant="primary" onClick={handleLogout}>
            로그아웃
          </Button>
        </div>
      </Dialog>
    </div>
  );
}
