import axiosInstance from './axiosInstance';

export const shopApi = {
  getCurrentUser: () => axiosInstance.get('/v1/auth/me'),
  getShop: (id) => axiosInstance.get(`/v1/shops/${id}`),
  updateShop: (id, payload) => axiosInstance.put(`/v1/shops/${id}`, payload),
  uploadImage: (file) => {
    const body = new FormData();
    body.append('file', file);
    return axiosInstance.put('/v1/shops/me/image', body, {
      headers: { 'Content-Type': undefined },
    });
  },
  deleteImage: () => axiosInstance.delete('/v1/shops/me/image'),
};
