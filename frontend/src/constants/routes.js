export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  SERVICE_DETAIL: '/services/:id',
  RESERVE: '/reserve/:id',
  CHECKOUT: '/checkout',
  BOOKING_RESULT: '/result',
  PROFILE: '/profile',
  BOOKINGS: '/bookings',
  // 고객 내 예약 (실제 API). BOOKINGS 는 모의 결제·환불 데모 화면이다
  MY_RESERVATIONS: '/my-reservations',
  // 고객 예약 취소 화면 (E 담당). 내 예약의 "취소하기"가 이 경로로 이동한다
  MY_RESERVATION_CANCEL: '/my-reservations/:reservationId/cancel',
  // 고객 취소 결과 (E 담당). 새로고침해도 서버 예약 상태로 다시 그린다
  MY_RESERVATION_CANCEL_RESULT: '/my-reservations/:reservationId/cancel/result',
  OWNER: '/owner',
  OWNER_SHOP: '/owner/shop',
  OWNER_MENUS: '/owner/menus',
  OWNER_SLOTS: '/owner/slots',
  OWNER_SLOT_NEW: '/owner/slots/new',
  OWNER_SCHEDULE: '/owner/schedule',
  OWNER_BOOKINGS: '/owner/bookings',
  // 사장님 예약 취소 화면 (E 담당). 예약 현황의 "취소하기"가 이 경로로 이동한다
  OWNER_RESERVATION_CANCEL: '/owner/bookings/:reservationId/cancel',
  // 사장님 취소 결과 (E 담당)
  OWNER_RESERVATION_CANCEL_RESULT: '/owner/bookings/:reservationId/cancel/result',
  // 이전 시연 주소(/design/...) 호환용. 접속 시 /design 을 뺀 주소로 이동한다.
  LEGACY_DESIGN: '/design/*',
  NOT_FOUND: '*',
};
