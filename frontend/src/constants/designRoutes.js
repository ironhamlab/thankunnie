import { ROUTES } from './routes';

export const DESIGN_ROUTES = {
  ROOT: '/design',
  HOME: ROUTES.HOME,
  EXPLORE: '/design/explore',
  EXPLORE_CLASSIC: '/design/explore-classic',
  DETAIL: '/design/services/:id',
  RESERVE: '/design/reserve/:id',
  CHECKOUT: '/design/checkout',
  RESULT: '/design/result',
  PROFILE: '/design/profile',
  BOOKINGS: '/design/bookings',
  OWNER: '/design/owner',
  SHOP: '/design/owner/shop',
  MENUS: '/design/owner/menus',
  SLOTS: '/design/owner/slots',
  SLOT_NEW: '/design/owner/slots/new',
  SCHEDULE: '/design/owner/schedule',
  OWNER_BOOKINGS: '/design/owner/bookings',
  // 사장님 예약 취소 화면 (E 담당). 예약 현황의 "취소하기"가 이 경로로 이동한다
  OWNER_RESERVATION_CANCEL: '/design/owner/bookings/:reservationId/cancel',
};
