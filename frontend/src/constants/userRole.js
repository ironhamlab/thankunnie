/**
 * 로그인 사용자 역할 (백엔드 UserRole, 로그인 응답의 role).
 */
export const USER_ROLE = {
  CUSTOMER: 'CUSTOMER',
  OWNER: 'OWNER',
  ADMIN: 'ADMIN',
};

// 화면 영역별 접근 가능한 역할. 관리자 화면이 생기기 전까지 관리자는 고객 영역을 쓴다
export const CUSTOMER_AREA_ROLES = [USER_ROLE.CUSTOMER, USER_ROLE.ADMIN];
export const OWNER_AREA_ROLES = [USER_ROLE.OWNER];
