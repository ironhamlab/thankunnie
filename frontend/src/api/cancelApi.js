import axiosInstance from './axiosInstance';

export const cancelApi = {
  /** 취소 환불 견적. 고객은 본인 예약, 매장은 본인 매장 예약 */
  getCancelQuote: (reservationId) =>
    axiosInstance.get(`/v1/reservations/${reservationId}/cancel-quote`),
  /** 고객 직접 취소. expectedRefundAmount 는 화면에 보여준 견적의 refundAmount */
  cancelByCustomer: (reservationId, expectedRefundAmount) =>
    axiosInstance.post(`/v1/reservations/${reservationId}/cancel`, { expectedRefundAmount }),
  /**
   * 매장 취소. body: { cancelType, refundAmount, closeAfterCancel, reason }
   * refundAmount 는 CUSTOMER_REQUEST 일 때만 쓰며, SHOP_FAULT 는 서버가 전액 환불한다
   */
  cancelByOwner: (reservationId, body) =>
    axiosInstance.post(`/v1/reservations/${reservationId}/owner-cancel`, body),
};
