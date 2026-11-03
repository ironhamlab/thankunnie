import axiosInstance from './axiosInstance';

export const termsApi = {
  /** 코드별 최신 버전 약관 목록 (로그인 불필요). target: TERMS_TARGET.CUSTOMER(고객 가입)·SHOP(매장 가입) */
  getTerms: (target) => axiosInstance.get('/v1/terms', { params: { target } }),
  /** 내가 동의한 약관 목록 (고객) */
  getMyAgreements: () => axiosInstance.get('/v1/users/me/terms-agreements'),
  /** 약관 동의. 이미 동의했으면 기존 동의를 그대로 돌려준다 */
  agree: (termsCode) => axiosInstance.put(`/v1/users/me/terms-agreements/${termsCode}`),
};
