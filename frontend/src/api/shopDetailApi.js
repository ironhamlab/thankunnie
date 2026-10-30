import axiosInstance from './axiosInstance';

export const shopDetailApi = {
  getShop: (shopId) => axiosInstance.get(`/v1/explore/shops/${shopId}`),
  getAvailableTimes: (shopId, date, durationMinutes) =>
    axiosInstance.get('/v1/available-times', { params: { shopId, date, durationMinutes } }),
};
