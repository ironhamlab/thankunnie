import axiosInstance from './axiosInstance';

export const paymentApi = {
  /**
   * 결제창이 끝난 뒤 서버가 포트원에 결제를 다시 확인하고 예약을 확정한다.
   * body: { impUid, reservationId, customerRequest }
   */
  verify: (body) => axiosInstance.post('/v1/payments/verify', body),
};
