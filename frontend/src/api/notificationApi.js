import axiosInstance from './axiosInstance';

export const notificationApi = {
  getMySubscriptions: () => axiosInstance.get('/v1/notifications/subscriptions'),
  createSubscription: (request) => axiosInstance.post('/v1/notifications/subscriptions', request),
  deleteSubscription: (subscriptionId) =>
    axiosInstance.delete(`/v1/notifications/subscriptions/${subscriptionId}`),
  // 알림 조건의 시술 선택지. 매장 가입용 카테고리 목록과 같은 값을 쓴다 (로그인 불필요)
  getCategories: () => axiosInstance.get('/v1/auth/shop-categories'),
};
