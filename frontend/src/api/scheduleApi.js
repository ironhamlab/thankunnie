import axiosInstance from './axiosInstance';

export const scheduleApi = {
  getCurrentUser: () => axiosInstance.get('/v1/auth/me'),
  getBusinessHours: () => axiosInstance.get('/v1/business-hours'),
  updateBusinessHours: (days, closeOpenExceptions) =>
    axiosInstance.put('/v1/business-hours', { days, closeOpenExceptions }),
  // 요일을 휴무로 바꾸기 전에 그 요일의 앞으로의 예약과 시간대 열기를 받는다
  getClosingImpact: (days) =>
    axiosInstance.get('/v1/business-hours/closing-impact', {
      params: { days },
      paramsSerializer: { indexes: null },
    }),
  getExceptions: (from, to) =>
    axiosInstance.get('/v1/schedule-exceptions', { params: { from, to } }),
  createException: (payload) => axiosInstance.post('/v1/schedule-exceptions', payload),
  updateException: (exceptionId, payload) =>
    axiosInstance.put(`/v1/schedule-exceptions/${exceptionId}`, payload),
  deleteException: (exceptionId) => axiosInstance.delete(`/v1/schedule-exceptions/${exceptionId}`),
  getAvailableTimes: (shopId, date) =>
    axiosInstance.get('/v1/available-times', { params: { shopId, date } }),
};
