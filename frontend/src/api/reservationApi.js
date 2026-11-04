import axiosInstance from './axiosInstance';

// 상태 필터는 status=A&status=B 로 보내야 백엔드가 목록으로 받는다 (axios 기본 배열 형식은 status[]=A)
const toParams = ({ from, to, statuses = [], timing, page = 0, size = 20 }) => {
  const params = new URLSearchParams();
  if (from) params.append('from', from);
  if (to) params.append('to', to);
  statuses.forEach((status) => params.append('status', status));
  if (timing) params.append('timing', timing);
  params.append('page', page);
  params.append('size', size);
  return params;
};

export const reservationApi = {
  // 고객이 고른 시술·시작 시각을 5분간 점유(HOLD)하는 예약을 만든다
  createReservation: (request) => axiosInstance.post('/v1/reservations', request),
  // 같은 빈자리(시술·시작 시각)를 누른 순서대로 줄에 서거나, 이미 서 있으면 지금 순서를 받는다
  joinQueue: (slot) => axiosInstance.post('/v1/reservations/queue', slot),
  // 대기를 취소한다. 줄에 없어도 성공(204)이다
  leaveQueue: (slot) => axiosInstance.post('/v1/reservations/queue/leave', slot),
  getOwnerReservations: (filters) =>
    axiosInstance.get('/v1/owner/reservations', { params: toParams(filters) }),
  getOwnerReservation: (reservationId) =>
    axiosInstance.get(`/v1/owner/reservations/${reservationId}`),
  getMyReservations: (filters) =>
    axiosInstance.get('/v1/my/reservations', { params: toParams(filters) }),
  getMyReservation: (reservationId) => axiosInstance.get(`/v1/my/reservations/${reservationId}`),
  // 지금 잡고 있는 결제 대기 예약(만료 전 HOLD). 없으면 null
  getMyCurrentHold: () => axiosInstance.get('/v1/my/reservations/current-hold'),
  // 결제 화면을 떠날 때 본인 점유를 푼다. 이미 만료됐어도 성공(204)이다
  releaseHold: (reservationId) =>
    axiosInstance.post(`/v1/my/reservations/${reservationId}/release`),
};
