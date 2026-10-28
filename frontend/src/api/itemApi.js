import axiosInstance from './axiosInstance';

/** C 탐색 API의 목록 응답은 공통 인터셉터가 data를 추출한 상태로 반환한다. */
export const itemApi = {
  search: (params) => axiosInstance.get('/v1/items', { params }),
};
