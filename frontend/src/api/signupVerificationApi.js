import axiosInstance from './axiosInstance';

export const signupVerificationApi = {
  verifyBusiness: (payload) => axiosInstance.post('/v1/auth/business/verify', payload),
  checkLoginId: (loginId, accountType) =>
    axiosInstance.post('/v1/auth/login-id/check', { loginId, accountType }),
  sendPhoneCode: (phone) => axiosInstance.post('/v1/auth/phone/send', { phone }),
  verifyPhoneCode: (verificationId, code) =>
    axiosInstance.post('/v1/auth/phone/verify', { verificationId, code }),
};
