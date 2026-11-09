import axiosInstance from './axiosInstance';

// regionCode 처럼 여러 값을 보내는 파라미터는 regionCode=a&regionCode=b 형태로 보낸다
const repeatParams = { indexes: null };

export const exploreApi = {
  getVacancies: (params) =>
    axiosInstance.get('/v1/explore/vacancies', { params, paramsSerializer: repeatParams }),
  // 메인 임박 카드. 8시간 안 일반 시술 빈자리를 매장마다 1건씩 빠른 시작순으로 최대 10개 (영업시간 기준, OPEN 불필요)
  getImminentVacancies: () => axiosInstance.get('/v1/explore/vacancies/imminent'),
  getShops: (params) =>
    axiosInstance.get('/v1/explore/shops', { params, paramsSerializer: repeatParams }),
  // 빈자리 유무와 상관없이 노출 중인 매장 전체
  getAllShops: (params) => axiosInstance.get('/v1/explore/shops/all', { params }),
  getCategories: () => axiosInstance.get('/v1/auth/shop-categories'),
};
