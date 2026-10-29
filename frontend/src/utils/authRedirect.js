import { ROUTES } from '@/constants/routes';

const isOwnerPath = (pathname) =>
  pathname === ROUTES.OWNER || pathname.startsWith(`${ROUTES.OWNER}/`);

/** 로그인·가입 직후 이동할 첫 화면. 사장님은 사장님 센터, 고객·관리자는 홈. */
export const getHomePathByRole = (role) => (role === 'OWNER' ? ROUTES.OWNER : ROUTES.HOME);

/**
 * 로그인 전에 보려던 화면(PrivateRoute 가 넘긴 location)으로 돌려보낸다.
 * 역할과 맞지 않는 화면(고객이 사장님 센터 등)이면 역할별 첫 화면으로 보낸다.
 */
export const getRedirectPath = (from, role) => {
  if (!from?.pathname || isOwnerPath(from.pathname) !== (role === 'OWNER')) {
    return getHomePathByRole(role);
  }
  return `${from.pathname}${from.search ?? ''}`;
};

/** 사장님 센터에서 로그인 화면으로 왔는지. 로그인 화면이 사장님 탭으로 열린다. */
export const isFromOwnerArea = (from) => Boolean(from?.pathname && isOwnerPath(from.pathname));
