import axiosInstance from './axiosInstance';

export const paymentApi = {
  /**
   * 결제창을 열기 전에 이 화면(탭)이 결제를 맡는다. 다른 탭이 결제 중이면 PAYMENT_008 로 거절된다.
   * body: { reservationId, tabId } → { merchantUid }
   */
  start: (body) => axiosInstance.post('/v1/payments/start', body),
  /**
   * 결제창을 닫았거나 결제가 실패해 결제를 그만둔다. 다른 탭이 바로 결제할 수 있다.
   * body: { reservationId, tabId }
   */
  cancelStart: (body) => axiosInstance.post('/v1/payments/start/cancel', body),
  /**
   * 결제창이 끝난 뒤 서버가 포트원에 결제를 다시 확인하고 예약을 확정한다.
   * body: { impUid, reservationId, customerRequest }
   */
  verify: (body) => axiosInstance.post('/v1/payments/verify', body),
};
