export const ROUTES = {
  HOME: '/',
  LOGIN: '/login',
  // C 화면 등록 후 사용할 임시 계약 경로. 상수 정의만으로 경로가 등록되지는 않는다.
  EXPLORE: '/explore',
  ITEM_DETAIL: '/items/:id',
  NOT_FOUND: '*',
};

// AppRouter에 실제 C 화면을 등록·검증한 뒤 해당 항목만 활성화한다.
export const MAIN_ROUTE_AVAILABILITY = Object.freeze({
  EXPLORE: false,
  ITEM_DETAIL: false,
});
