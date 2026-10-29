import axiosInstance from './axiosInstance';

// regionCode 처럼 여러 값을 보내는 파라미터는 regionCode=a&regionCode=b 형태로 보낸다
const repeatParams = { indexes: null };

export const exploreApi = {
  getVacancies: (params) =>
    axiosInstance.get('/v1/explore/vacancies', { params, paramsSerializer: repeatParams }),
  getShops: (params) =>
    axiosInstance.get('/v1/explore/shops', { params, paramsSerializer: repeatParams }),
  getCategories: () => axiosInstance.get('/v1/auth/shop-categories'),
};
