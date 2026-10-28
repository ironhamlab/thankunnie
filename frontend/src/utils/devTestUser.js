// 실제 로그인 전까지 개발 서버에서만 쓰는 테스트 계정 전환.
// 백엔드 local·test 프로필이 Authorization: Bearer {테스트 사용자} 를 받는다. 운영 빌드에서는 쓰지 않는다.
const STORAGE_KEY = 'thankunnie.devTestUser';
const ANONYMOUS = '';

export const isDevTestUserEnabled = import.meta.env.DEV;

export const TEST_USERS = [
  { value: ANONYMOUS, label: '비로그인' },
  { value: 'CUSTOMER_1', label: '고객 1 (고객일)' },
  { value: 'CUSTOMER_2', label: '고객 2 (고객이)' },
  { value: 'OWNER_1', label: '사장님 1 (매장 1)' },
  { value: 'OWNER_2', label: '사장님 2 (매장 2)' },
  { value: 'ADMIN', label: '관리자' },
];

const readStored = () => {
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
};

/**
 * 현재 테스트 사용자. 스위치에서 고른 값 → .env.local 의 VITE_TEST_USER 순서로 쓰고, 없으면 null(비로그인).
 */
export const getDevTestUser = () => {
  if (!isDevTestUserEnabled) {
    return null;
  }
  const stored = readStored();
  if (stored !== null) {
    return stored || null;
  }
  return import.meta.env.VITE_TEST_USER || null;
};

/**
 * 테스트 사용자를 바꾼다. 비로그인도 저장해 VITE_TEST_USER 기본값보다 우선하게 한다.
 */
export const setDevTestUser = (value) => {
  try {
    window.localStorage.setItem(STORAGE_KEY, value ?? ANONYMOUS);
  } catch {
    // 저장소를 쓸 수 없으면 VITE_TEST_USER 기본값을 그대로 쓴다
  }
};
