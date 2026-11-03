import axiosInstance from './axiosInstance';

const URL = '/v1/admin/shop-applications';

export const adminApi = {
  getShopApplications: (params) => axiosInstance.get(URL, { params }),
  approveShopApplication: (shopId) => axiosInstance.patch(`${URL}/${shopId}/approve`),
  rejectShopApplication: (shopId, reason) =>
    axiosInstance.patch(`${URL}/${shopId}/reject`, { reason }),
  cancelShopApproval: (shopId, reason) =>
    axiosInstance.patch(`${URL}/${shopId}/cancel-approval`, { reason }),
  cancelShopRejection: (shopId) => axiosInstance.patch(`${URL}/${shopId}/cancel-rejection`),
};
