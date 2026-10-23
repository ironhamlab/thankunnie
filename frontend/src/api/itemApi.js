/** 공통 응답의 data를 반환하는 팀 axiosInstance를 주입한다. */
export function createItemApi(axiosInstance) {
  return {
    search: (params) => axiosInstance.get('/v1/items', { params }),
  };
}
