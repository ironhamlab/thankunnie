export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  SERVICE_DETAIL: '/services/:id',
  RESERVE: '/reserve/:id',
  CHECKOUT: '/checkout',
  BOOKING_RESULT: '/result',
  PROFILE: '/profile',
  BOOKINGS: '/bookings',
  OWNER: '/owner',
  OWNER_SHOP: '/owner/shop',
  OWNER_MENUS: '/owner/menus',
  OWNER_SLOTS: '/owner/slots',
  OWNER_SLOT_NEW: '/owner/slots/new',
  OWNER_SCHEDULE: '/owner/schedule',
  OWNER_BOOKINGS: '/owner/bookings',
  // 이전 시연 주소(/design/...) 호환용. 접속 시 /design 을 뺀 주소로 이동한다.
  LEGACY_DESIGN: '/design/*',
  // pages/main(API 메인 보존 코드)이 사용하는 임시 계약 경로. 상수 정의만으로 경로가 등록되지는 않는다.
  EXPLORE: '/explore',
  ITEM_DETAIL: '/items/:id',
  NOT_FOUND: '*',
};

// AppRouter에 실제 C 화면을 등록·검증한 뒤 해당 항목만 활성화한다.
export const MAIN_ROUTE_AVAILABILITY = Object.freeze({
  EXPLORE: false,
  ITEM_DETAIL: false,
});
