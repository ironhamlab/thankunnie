import axiosInstance from './axiosInstance';

export const scheduleApi = {
  getCurrentUser: () => axiosInstance.get('/v1/auth/me'),
  getBusinessHours: () => axiosInstance.get('/v1/business-hours'),
  updateBusinessHours: (days) => axiosInstance.put('/v1/business-hours', { days }),
  getExceptions: (from, to) =>
    axiosInstance.get('/v1/schedule-exceptions', { params: { from, to } }),
  createException: (payload) => axiosInstance.post('/v1/schedule-exceptions', payload),
  updateException: (exceptionId, payload) =>
    axiosInstance.put(`/v1/schedule-exceptions/${exceptionId}`, payload),
  deleteException: (exceptionId) => axiosInstance.delete(`/v1/schedule-exceptions/${exceptionId}`),
  getAvailableTimes: (shopId, date) =>
    axiosInstance.get('/v1/available-times', { params: { shopId, date } }),
};
