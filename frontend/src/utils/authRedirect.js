import { ROUTES } from '@/constants/routes';

const isUnder = (pathname, area) => pathname === area || pathname.startsWith(`${area}/`);
const isOwnerPath = (pathname) => isUnder(pathname, ROUTES.OWNER);
const isAdminPath = (pathname) => isUnder(pathname, ROUTES.ADMIN);

const HOME_BY_ROLE = { OWNER: ROUTES.OWNER, ADMIN: ROUTES.ADMIN };

/** 로그인·가입 직후 이동할 첫 화면. 매장은 매장 센터, 관리자는 관리자 화면, 고객은 홈. */
export const getHomePathByRole = (role) => HOME_BY_ROLE[role] ?? ROUTES.HOME;

/**
 * 로그인 전에 보려던 화면(PrivateRoute 가 넘긴 location)으로 돌려보낸다.
 * 역할과 맞지 않는 화면(고객이 매장 센터 등)이면 역할별 첫 화면으로 보낸다.
 */
export const getRedirectPath = (from, role) => {
  if (
    !from?.pathname ||
    isOwnerPath(from.pathname) !== (role === 'OWNER') ||
    isAdminPath(from.pathname) !== (role === 'ADMIN')
  ) {
    return getHomePathByRole(role);
  }
  return `${from.pathname}${from.search ?? ''}`;
};

/** 매장 센터에서 로그인 화면으로 왔는지. 로그인 화면이 매장 탭으로 열린다. */
export const isFromOwnerArea = (from) => Boolean(from?.pathname && isOwnerPath(from.pathname));
