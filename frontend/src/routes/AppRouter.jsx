import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import MainLayout from '@/components/layout/MainLayout';
import OwnerLayout from '@/components/layout/OwnerLayout';
import Spinner from '@/components/common/Spinner';
import PrivateRoute from '@/routes/PrivateRoute';
import { ROUTES } from '@/constants/routes';

const HomePage = lazy(() => import('@/pages/home/HomePage'));
const LoginPage = lazy(() => import('@/pages/auth/LoginPage'));
const SignupPage = lazy(() => import('@/pages/auth/SignupPage'));
const ShopSignupPage = lazy(() => import('@/pages/auth/ShopSignupPage'));
const NotFoundPage = lazy(() => import('@/pages/not-found/NotFoundPage'));
const LegacyServiceDetailRedirect = lazy(() => import('@/pages/shop/LegacyServiceDetailRedirect'));
const ShopDetailPage = lazy(() => import('@/pages/shop/ShopDetailPage'));
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
const SlotsPage = lazy(() => import('@/pages/owner/SlotsPage'));
const NewSlotPage = lazy(() => import('@/pages/owner/NewSlotPage'));
const SchedulePage = lazy(() => import('@/pages/owner/SchedulePage'));
const OwnerBookingsPage = lazy(() => import('@/pages/owner/OwnerBookingsPage'));

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
        <Route element={<MainLayout />}>
          <Route path={ROUTES.HOME} element={<HomePage />} />
          <Route path={ROUTES.LOGIN} element={<LoginPage />} />
          <Route path={ROUTES.SIGNUP} element={<SignupPage />} />
          <Route path={ROUTES.SHOP_SIGNUP} element={<ShopSignupPage />} />
          <Route path={ROUTES.SERVICE_DETAIL} element={<LegacyServiceDetailRedirect />} />
          <Route path={ROUTES.SHOP_DETAIL} element={<ShopDetailPage />} />
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
        {/* 사장님 센터는 로그인한 뒤에 들어간다 */}
        <Route element={<PrivateRoute />}>
          <Route path={ROUTES.OWNER} element={<OwnerLayout />}>
            <Route index element={<OwnerDashboardPage />} />
            <Route path={ROUTES.OWNER_SHOP} element={<ShopPage />} />
            <Route path={ROUTES.OWNER_MENUS} element={<MenusPage />} />
            <Route path={ROUTES.OWNER_SLOTS} element={<SlotsPage />} />
            <Route path={ROUTES.OWNER_SLOT_NEW} element={<NewSlotPage />} />
            <Route path={ROUTES.OWNER_SCHEDULE} element={<SchedulePage />} />
            <Route path={ROUTES.OWNER_BOOKINGS} element={<OwnerBookingsPage />} />
            <Route path={ROUTES.OWNER_RESERVATION_CANCEL} element={<OwnerCancelPage />} />
            <Route
              path={ROUTES.OWNER_RESERVATION_CANCEL_RESULT}
              element={<CancelResultPage viewer="owner" />}
            />
            <Route path={ROUTES.NOT_FOUND} element={<NotFoundPage />} />
          </Route>
        </Route>
        <Route path={ROUTES.LEGACY_DESIGN} element={<LegacyDesignRedirect />} />
      </Routes>
    </Suspense>
  );
}
