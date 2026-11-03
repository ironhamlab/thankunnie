import axiosInstance from './axiosInstance';

export const userApi = {
  /** 내 회원 정보 (docs/api/users.md). 고객·관리자만 조회할 수 있다 */
  getMyProfile: () => axiosInstance.get('/v1/users/me'),
  /** 회원 탈퇴. body: { password }. 성공하면 본문 없이 204 */
  withdraw: (payload) => axiosInstance.post('/v1/users/me/withdrawal', payload),
};
