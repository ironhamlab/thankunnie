import axiosInstance from './axiosInstance';

export const userApi = {
  /** 내 회원 정보 (docs/api/users.md). 고객·관리자만 조회할 수 있다 */
  getMyProfile: () => axiosInstance.get('/v1/users/me'),
};
