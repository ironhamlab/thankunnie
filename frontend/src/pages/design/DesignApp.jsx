import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { DESIGN_ROUTES as R } from '@/constants/designRoutes';
import DesignLayout from './DesignLayout';
import './design.css';
import './remaining-screens.css';

const DesignHomePage = lazy(() => import('./DesignHomePage'));
const ReservationPage = lazy(() => import('./ReservationPage'));
const CheckoutPage = lazy(() => import('./CheckoutPage'));
const BookingsPage = lazy(() => import('./BookingsPage'));
const OwnerPage = lazy(() => import('./OwnerPage'));
const ServiceDetailPage = lazy(() => import('./ServiceDetailPage'));
const BookingResultPage = lazy(() => import('./BookingResultPage'));
const CustomerProfilePage = lazy(() => import('./CustomerProfilePage'));

export default function DesignApp() {
  return (
    <div className="beauty-app">
      <Suspense
        fallback={
          <div className="b-empty" role="status">
            화면을 불러오는 중입니다.
          </div>
        }
      >
        <Routes>
          <Route element={<DesignLayout />}>
            <Route index element={<DesignHomePage />} />
            <Route path="explore" element={<Navigate to={`${R.HOME}?view=map`} replace />} />
            <Route
              path="explore-classic"
              element={<Navigate to={`${R.HOME}?view=map`} replace />}
            />
            <Route path="reserve/:id" element={<ReservationPage />} />
            <Route path="services/:id" element={<ServiceDetailPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="result" element={<BookingResultPage />} />
            <Route path="profile" element={<CustomerProfilePage />} />
            <Route path="bookings" element={<BookingsPage />} />
            <Route path="owner/*" element={<OwnerPage />} />
            <Route
              path="*"
              element={
                <div className="b-empty">
                  <h1>화면을 찾을 수 없어요</h1>
                  <a href={R.HOME}>빈자리 탐색으로</a>
                </div>
              }
            />
          </Route>
        </Routes>
      </Suspense>
    </div>
  );
}
