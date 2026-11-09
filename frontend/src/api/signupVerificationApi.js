import axiosInstance from './axiosInstance';

export const signupVerificationApi = {
  verifyBusiness: (payload) => axiosInstance.post('/v1/auth/business/verify', payload),
  checkLoginId: (loginId, accountType) =>
    axiosInstance.post('/v1/auth/login-id/check', { loginId, accountType }),
  // accountType 이 shop 이면 서버가 휴대폰 번호 중복을 검사하지 않는다
  sendPhoneCode: (phone, accountType) =>
    axiosInstance.post('/v1/auth/phone/send', { phone, accountType }),
  verifyPhoneCode: (verificationId, code) =>
    axiosInstance.post('/v1/auth/phone/verify', { verificationId, code }),
};
