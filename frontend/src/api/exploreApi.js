import axiosInstance from './axiosInstance';

export const exploreApi = {
  search: (params) =>
    axiosInstance.get('/v1/explore/vacancies', {
      params,
      paramsSerializer: { indexes: null },
    }),
};
