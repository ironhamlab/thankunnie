import axiosInstance from './axiosInstance';

const basePath = (shopId) => `/v1/shops/${shopId}/treatments`;

export const treatmentApi = {
  uploadImage: (treatmentId, file) => {
    const body = new FormData();
    body.append('file', file);
    return axiosInstance.put(`/v1/shops/me/treatments/${treatmentId}/image`, body, {
      headers: { 'Content-Type': undefined },
    });
  },
  deleteImage: (treatmentId) =>
    axiosInstance.delete(`/v1/shops/me/treatments/${treatmentId}/image`),
  getCurrentUser: () => axiosInstance.get('/v1/auth/me'),
  getTreatments: (shopId) => axiosInstance.get(basePath(shopId)),
  getActiveTreatments: (shopId) =>
    axiosInstance.get(basePath(shopId), { params: { status: 'ACTIVE' } }),
  createTreatment: (shopId, payload) => axiosInstance.post(basePath(shopId), payload),
  updateTreatment: (shopId, treatmentId, payload) =>
    axiosInstance.put(`${basePath(shopId)}/${treatmentId}`, payload),
  updateStatus: (shopId, treatmentId, status) =>
    axiosInstance.patch(`${basePath(shopId)}/${treatmentId}/status`, { status }),
};
