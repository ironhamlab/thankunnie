import axiosInstance from './axiosInstance';

/**
 * 인증 API (docs/api/auth.md).
 * 고객·관리자는 회원 계정, 매장은 매장 계정으로 가입·로그인한다. 성공하면 토큰 응답을 받는다.
 */
export const authApi = {
  /** 고객·관리자 로그인. body: { loginId, password } */
  login: (payload) => axiosInstance.post('/v1/auth/login', payload),
  /** 매장 로그인. body: { loginId, password } */
  loginShop: (payload) => axiosInstance.post('/v1/auth/shops/login', payload),
  /** 고객 가입. body: { loginId, password, name, phone, agreedTerms } (agreedTerms: 동의한 약관 코드 배열) */
  signup: (payload) => axiosInstance.post('/v1/auth/signup', payload),
  /** 매장 가입 신청. 계정과 매장 필수 정보를 함께 보낸다. 승인 대기라 토큰 없이 { shopId, status } 를 받는다 */
  signupShop: (payload) => axiosInstance.post('/v1/auth/shops/signup', payload),
  /** 매장 가입용 카테고리 목록 (비로그인) */
  getShopCategories: () => axiosInstance.get('/v1/auth/shop-categories'),
};
