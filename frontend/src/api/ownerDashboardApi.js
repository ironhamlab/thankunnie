import axiosInstance from './axiosInstance';

export const ownerDashboardApi = {
  // 오늘 지표(남은 빈 시간·휴무 여부 포함)와 이번 달 요약. 매장은 로그인한 매장 계정의 매장
  getDashboard: () => axiosInstance.get('/v1/owner/dashboard'),
  // 기간 통계. from·to 는 YYYY-MM-DD(둘 다 포함, 최대 92일), 생략하면 이번 달
  getStats: ({ from, to } = {}) =>
    axiosInstance.get('/v1/owner/dashboard/stats', { params: { from, to } }),
};
