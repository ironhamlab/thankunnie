import { create } from 'zustand';
import { signupVerificationApi } from '@/api/signupVerificationApi';

// 입력값과 인증 결과는 가입 화면에만 두고, 업무 API는 store 액션을 통해 호출한다.
export const useSignupVerificationStore = create(() => ({
  verifyBusiness: signupVerificationApi.verifyBusiness,
  checkLoginId: signupVerificationApi.checkLoginId,
  sendPhoneCode: signupVerificationApi.sendPhoneCode,
  verifyPhoneCode: signupVerificationApi.verifyPhoneCode,
}));
