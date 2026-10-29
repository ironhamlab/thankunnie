import axiosInstance from './axiosInstance';

export const serviceDetailApi = {
  getDetail: (treatmentId, startsAt) =>
    axiosInstance.get(`/v1/explore/vacancies/${treatmentId}`, { params: { startsAt } }),
};
