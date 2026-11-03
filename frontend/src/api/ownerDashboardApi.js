import axiosInstance from './axiosInstance';

export const ownerDashboardApi = {
  // 오늘 지표, 내일 예약, 이번 달 누적(지난달 같은 기간 비교), 찜 고객 수. 매장은 로그인한 사장님 매장
  getDashboard: () => axiosInstance.get('/v1/owner/dashboard'),
  // 매출·예약 분석. from·to 는 YYYY-MM-DD(둘 다 포함, 최대 3660일), 생략하면 이번 달.
  // all 이면 from·to 대신 서비스 이용 시작일~오늘
  getStats: ({ from, to, all } = {}) =>
    axiosInstance.get('/v1/owner/dashboard/stats', {
      params: all ? { all: true } : { from, to },
    }),
};
