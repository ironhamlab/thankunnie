import axiosInstance from './axiosInstance';

const basePath = (shopId) => `/v1/shops/${shopId}/treatments`;

export const treatmentApi = {
  getCurrentUser: () => axiosInstance.get('/v1/auth/me'),
  getTreatments: (shopId) => axiosInstance.get(basePath(shopId)),
  getActiveTreatments: (shopId) => axiosInstance.get(basePath(shopId), { params: { status: 'ACTIVE' } }),
  getCategories: (shopId) => axiosInstance.get(`${basePath(shopId)}/categories`),
  createTreatment: (shopId, payload) => axiosInstance.post(basePath(shopId), payload),
  updateTreatment: (shopId, treatmentId, payload) =>
    axiosInstance.put(`${basePath(shopId)}/${treatmentId}`, payload),
  updateStatus: (shopId, treatmentId, status) =>
    axiosInstance.patch(`${basePath(shopId)}/${treatmentId}/status`, { status }),
};
