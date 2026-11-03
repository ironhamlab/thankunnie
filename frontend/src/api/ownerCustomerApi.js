import axiosInstance from './axiosInstance';

export const ownerCustomerApi = {
  // 고객 목록. segment: ALL·NEW·RETURNING, sort: RECENT·VISITS·AMOUNT, keyword: 이름·연락처 일부
  getCustomers: ({ segment, sort, keyword, page, size } = {}) =>
    axiosInstance.get('/v1/owner/customers', {
      params: { segment, sort, keyword: keyword || undefined, page, size },
    }),
  // 고객 상세: 요약(연락처 전체), 매장 메모, 예약 이력 최근 50건
  getCustomer: (userId) => axiosInstance.get(`/v1/owner/customers/${userId}`),
  // 매장 메모 저장. 비우면 지우고 null 을 돌려준다
  saveMemo: (userId, memo) => axiosInstance.put(`/v1/owner/customers/${userId}/memo`, { memo }),
};
