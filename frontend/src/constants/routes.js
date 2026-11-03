export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  // 고객 회원가입. 사장님은 매장 정보와 함께 가입한다
  SIGNUP: '/signup',
  SHOP_SIGNUP: '/signup/shop',
  // 이전 시술 상세 주소. 라우트는 없으며, 홈·지도·마이페이지 시연 링크가 매장 상세(SHOP_DETAIL)로 바뀌면 지운다
  SERVICE_DETAIL: '/services/:id',
  // 매장 상세: 매장 정보 → 시술 선택 → 날짜·시간 선택
  SHOP_DETAIL: '/shops/:shopId',
  // 초보 시술 모집 상세: 매장 상세와 같은 흐름으로 그 매장의 초보 시술만 보여주고 참여 조건·촬영 안내를 함께 보여준다
  MODEL_RECRUIT_DETAIL: '/shops/:shopId/models',
  // 지도 메인: 지도 범위의 빈자리 매장 핀과 매장 목록 (C 담당)
  MAP: '/map',
  RESERVE: '/reserve/:id',
  CHECKOUT: '/checkout',
  BOOKING_RESULT: '/result',
  PROFILE: '/profile',
  // 고객 내 예약 (실제 API)
  MY_RESERVATIONS: '/my-reservations',
  // 고객 예약 취소 화면 (E 담당). 내 예약의 "취소하기"가 이 경로로 이동한다
  MY_RESERVATION_CANCEL: '/my-reservations/:reservationId/cancel',
  // 고객 취소 결과 (E 담당). 새로고침해도 서버 예약 상태로 다시 그린다
  MY_RESERVATION_CANCEL_RESULT: '/my-reservations/:reservationId/cancel/result',
  OWNER: '/owner',
  OWNER_SHOP: '/owner/shop',
  OWNER_MENUS: '/owner/menus',
  OWNER_SCHEDULE: '/owner/schedule',
  // 빈자리 열기·닫기: 시간대를 열면 그 안에서 할 수 있는 시술이 고객 빈자리 목록에 노출되고, 닫으면 예약을 받지 않는다
  OWNER_VACANCIES: '/owner/vacancies',
  OWNER_BOOKINGS: '/owner/bookings',
  // 사장님 예약 취소 화면 (E 담당). 예약 현황의 "취소하기"가 이 경로로 이동한다
  OWNER_RESERVATION_CANCEL: '/owner/bookings/:reservationId/cancel',
  // 사장님 취소 결과 (E 담당)
  OWNER_RESERVATION_CANCEL_RESULT: '/owner/bookings/:reservationId/cancel/result',
  // 관리자: 매장 가입 신청 승인·거절
  ADMIN: '/admin',
  // 이전 시연 주소(/design/...) 호환용. 접속 시 /design 을 뺀 주소로 이동한다.
  LEGACY_DESIGN: '/design/*',
  NOT_FOUND: '*',
};
