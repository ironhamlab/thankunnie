import axiosInstance from './axiosInstance';

// 상태 필터는 status=A&status=B 로 보내야 백엔드가 목록으로 받는다 (axios 기본 배열 형식은 status[]=A)
const toParams = ({ from, to, statuses = [], page = 0, size = 20 }) => {
  const params = new URLSearchParams();
  if (from) params.append('from', from);
  if (to) params.append('to', to);
  statuses.forEach((status) => params.append('status', status));
  params.append('page', page);
  params.append('size', size);
  return params;
};

export const reservationApi = {
  // 고객이 고른 시술·시작 시각을 5분간 점유(HOLD)하는 예약을 만든다
  createReservation: (request) => axiosInstance.post('/v1/reservations', request),
  getOwnerReservations: (filters) =>
    axiosInstance.get('/v1/owner/reservations', { params: toParams(filters) }),
  getOwnerReservation: (reservationId) =>
    axiosInstance.get(`/v1/owner/reservations/${reservationId}`),
  getMyReservations: (filters) =>
    axiosInstance.get('/v1/my/reservations', { params: toParams(filters) }),
  getMyReservation: (reservationId) => axiosInstance.get(`/v1/my/reservations/${reservationId}`),
};
