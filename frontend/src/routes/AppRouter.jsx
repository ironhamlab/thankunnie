import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import MainLayout from '@/components/layout/MainLayout';
import AdminLayout from '@/components/layout/AdminLayout';
import OwnerLayout from '@/components/layout/OwnerLayout';
import Spinner from '@/components/common/Spinner';
import PrivateRoute from '@/routes/PrivateRoute';
import RoleRoute from '@/routes/RoleRoute';
import { ROUTES } from '@/constants/routes';
import { ADMIN_AREA_ROLES, CUSTOMER_AREA_ROLES, OWNER_AREA_ROLES } from '@/constants/userRole';

const HomePage = lazy(() => import('@/pages/home/HomePage'));
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'));
const SignupPage = lazy(() => import('@/pages/auth/SignupPage'));
const ShopSignupPage = lazy(() => import('@/pages/auth/ShopSignupPage'));
const NotFoundPage = lazy(() => import('@/pages/not-found/NotFoundPage'));
const ShopDetailPage = lazy(() => import('@/pages/shop/ShopDetailPage'));
const MapPage = lazy(() => import('@/pages/map/MapPage'));
const ReservationPage = lazy(() => import('@/pages/reservation/ReservationPage'));
const CheckoutPage = lazy(() => import('@/pages/reservation/CheckoutPage'));
const BookingResultPage = lazy(() => import('@/pages/reservation/BookingResultPage'));
const MyReservationsPage = lazy(() => import('@/pages/my/MyReservationsPage'));
const CustomerProfilePage = lazy(() => import('@/pages/my/CustomerProfilePage'));
const CustomerCancelPage = lazy(() => import('@/pages/cancel/CustomerCancelPage'));
const CancelResultPage = lazy(() => import('@/pages/cancel/CancelResultPage'));
const OwnerCancelPage = lazy(() => import('@/pages/cancel/OwnerCancelPage'));
const OwnerDashboardPage = lazy(() => import('@/pages/owner/OwnerDashboardPage'));
const ShopPage = lazy(() => import('@/pages/owner/ShopPage'));
const MenusPage = lazy(() => import('@/pages/owner/MenusPage'));
const SchedulePage = lazy(() => import('@/pages/owner/SchedulePage'));
const VacancyManagePage = lazy(() => import('@/pages/owner/VacancyManagePage'));
const OwnerBookingsPage = lazy(() => import('@/pages/owner/OwnerBookingsPage'));
const AdminShopApplicationsPage = lazy(() => import('@/pages/admin/AdminShopApplicationsPage'));

const LEGACY_EXPLORE_PATHS = ['/explore', '/explore-classic'];

/** 이전 시연 주소(/design/...)를 같은 화면의 새 주소로 보낸다. */
function LegacyDesignRedirect() {
  const { pathname, search } = useLocation();
  const path = pathname.replace(/^\/design/, '') || ROUTES.HOME;
  if (LEGACY_EXPLORE_PATHS.includes(path)) {
    return <Navigate to={`${ROUTES.HOME}?view=map`} replace />;
  }
  return <Navigate to={`${path}${search}`} replace />;
}

export default function AppRouter() {
  return (
    <Suspense fallback={<Spinner />}>
      <Routes>
        {/* 로그인·가입 화면은 이미 로그인한 경우를 각 화면에서 안내하므로 역할 가드 밖에 둔다 */}
        <Route element={<MainLayout />}>
          <Route path={ROUTES.LOGIN} element={<LoginPage />} />
          <Route path={ROUTES.SIGNUP} element={<SignupPage />} />
          <Route path={ROUTES.SHOP_SIGNUP} element={<ShopSignupPage />} />
        </Route>
        {/* 고객 화면. 사장님 계정이면 홈은 사장님 센터로 보내고, 나머지는 로그아웃 확인 창을 띄운다 */}
        <Route
          element={
            <RoleRoute
              allow={CUSTOMER_AREA_ROLES}
              areaName="고객 화면"
              redirectPaths={[ROUTES.HOME]}
            />
          }
        >
          <Route element={<MainLayout />}>
            <Route path={ROUTES.HOME} element={<HomePage />} />
            <Route path={ROUTES.SHOP_DETAIL} element={<ShopDetailPage />} />
            <Route
              path={ROUTES.MODEL_RECRUIT_DETAIL}
              element={<ShopDetailPage key="model" model />}
            />
            <Route path={ROUTES.MAP} element={<MapPage />} />
            <Route path={ROUTES.RESERVE} element={<ReservationPage />} />
            <Route path={ROUTES.CHECKOUT} element={<CheckoutPage />} />
            <Route path={ROUTES.BOOKING_RESULT} element={<BookingResultPage />} />
            {/* 실제 API 로 로그인 사용자 데이터를 불러오는 화면은 로그인이 필요하다 */}
            <Route element={<PrivateRoute />}>
              <Route path={ROUTES.PROFILE} element={<CustomerProfilePage />} />
              <Route path={ROUTES.MY_RESERVATIONS} element={<MyReservationsPage />} />
              <Route path={ROUTES.MY_RESERVATION_CANCEL} element={<CustomerCancelPage />} />
              <Route
                path={ROUTES.MY_RESERVATION_CANCEL_RESULT}
                element={<CancelResultPage viewer="customer" />}
              />
            </Route>
            <Route path={ROUTES.NOT_FOUND} element={<NotFoundPage />} />
          </Route>
        </Route>
        {/* 사장님 센터는 사장님으로 로그인한 뒤에 들어간다. 고객 계정이면 레이아웃(매장 조회)도 열지 않는다 */}
        <Route element={<PrivateRoute />}>
          <Route element={<RoleRoute allow={OWNER_AREA_ROLES} areaName="사장님 센터" />}>
            <Route path={ROUTES.OWNER} element={<OwnerLayout />}>
              <Route index element={<OwnerDashboardPage />} />
              <Route path={ROUTES.OWNER_SHOP} element={<ShopPage />} />
              <Route path={ROUTES.OWNER_MENUS} element={<MenusPage />} />
              <Route path={ROUTES.OWNER_SCHEDULE} element={<SchedulePage />} />
              <Route path={ROUTES.OWNER_VACANCIES} element={<VacancyManagePage />} />
              <Route path={ROUTES.OWNER_BOOKINGS} element={<OwnerBookingsPage />} />
              <Route path={ROUTES.OWNER_RESERVATION_CANCEL} element={<OwnerCancelPage />} />
              <Route
                path={ROUTES.OWNER_RESERVATION_CANCEL_RESULT}
                element={<CancelResultPage viewer="owner" />}
              />
              <Route path={ROUTES.NOT_FOUND} element={<NotFoundPage />} />
            </Route>
          </Route>
        </Route>
        {/* 관리자 화면은 관리자로 로그인한 뒤에 들어간다 */}
        <Route element={<PrivateRoute />}>
          <Route element={<RoleRoute allow={ADMIN_AREA_ROLES} areaName="관리자 화면" />}>
            <Route path={ROUTES.ADMIN} element={<AdminLayout />}>
              <Route index element={<AdminShopApplicationsPage />} />
              <Route path={ROUTES.NOT_FOUND} element={<NotFoundPage />} />
            </Route>
          </Route>
        </Route>
        <Route path={ROUTES.LEGACY_DESIGN} element={<LegacyDesignRedirect />} />
      </Routes>
    </Suspense>
  );
}
