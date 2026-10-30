import { create } from 'zustand';
import { reservationApi } from '@/api/reservationApi';
import { withTermsAgreement } from '@/store/useTermsStore';

// 사장님 예약 현황(owner)과 고객 내 예약(my)이 같은 응답 모양을 쓰므로 조회 범위만 바꿔 호출한다.
const API_BY_SCOPE = {
  owner: { list: reservationApi.getOwnerReservations, detail: reservationApi.getOwnerReservation },
  my: { list: reservationApi.getMyReservations, detail: reservationApi.getMyReservation },
};

const EMPTY_PAGE = { page: 0, totalPages: 0, totalElements: 0, hasNext: false };

const toError = (error) => ({
  code: error?.code ?? 'UNKNOWN_ERROR',
  message: error?.message ?? '예약 정보를 불러오지 못했습니다.',
});

export const useReservationStore = create((set, get) => ({
  reservations: [],
  pageInfo: EMPTY_PAGE,
  listStatus: 'idle',
  listError: null,
  listRequestId: 0,
  detail: null,
  detailStatus: 'idle',
  detailError: null,
  detailRequestId: 0,
  // 서버 시각 - 기기 시각(ms). 기기 시계가 틀려도 점유 남은 시간을 서버 기준으로 세기 위해 응답을 받은 순간 잰다
  detailClockOffset: 0,

  // 필터를 빠르게 바꾸면 요청이 겹치므로 마지막 요청의 응답만 반영한다.
  loadReservations: async (scope, filters) => {
    const requestId = get().listRequestId + 1;
    set({ listRequestId: requestId, listStatus: 'loading', listError: null });
    try {
      const result = await API_BY_SCOPE[scope].list(filters);
      if (get().listRequestId === requestId) {
        const { content, ...pageInfo } = result;
        set({ reservations: content, pageInfo, listStatus: 'succeeded' });
      }
    } catch (error) {
      if (get().listRequestId === requestId) {
        set({
          reservations: [],
          pageInfo: EMPTY_PAGE,
          listStatus: 'failed',
          listError: toError(error),
        });
      }
    }
  },

  loadReservation: async (scope, reservationId) => {
    const requestId = get().detailRequestId + 1;
    set({ detailRequestId: requestId, detailStatus: 'loading', detailError: null });
    try {
      const detail = await API_BY_SCOPE[scope].detail(reservationId);
      if (get().detailRequestId === requestId) {
        const serverTime = Date.parse(detail?.serverTime);
        set({
          detail,
          detailStatus: 'succeeded',
          detailClockOffset: Number.isNaN(serverTime) ? 0 : serverTime - Date.now(),
        });
      }
    } catch (error) {
      if (get().detailRequestId === requestId) {
        set({ detail: null, detailStatus: 'failed', detailError: toError(error) });
      }
    }
  },

  // 예약 생성은 화면마다 따로 진행 상태를 보여주므로 결과만 돌려주고 store 상태는 바꾸지 않는다.
  createReservation: async (request) => {
    try {
      // 모델 시술인데 촬영 동의가 없으면(TERMS_004) 동의 모달을 띄우고, 동의하면 한 번 다시 예약한다
      return await withTermsAgreement(() => reservationApi.createReservation(request));
    } catch (error) {
      throw toError(error);
    }
  },

  // 결제 화면을 떠날 때 부른다. 실패해도 이동은 막지 않으므로(5분 뒤 자동 만료) 오류를 던지지 않고 결과만 돌려준다.
  releaseHold: async (reservationId) => {
    try {
      await reservationApi.releaseHold(reservationId);
      return true;
    } catch {
      return false;
    }
  },

  // 다른 시간을 눌렀다가 결제 대기 중인 예약이 있다는 답(RESERVATION_006)을 받았을 때 그 예약을 찾는다.
  // 결제 화면 상세 상태를 덮어쓰지 않도록 store 에는 넣지 않고 결과(요약)만 돌려준다.
  // 서버가 1인 1점유 검사와 같은 기준(만료 전 HOLD)으로 찾으므로 만료 처리 전인 HOLD 를 잘못 보여주지 않는다.
  // 예약이 없으면 null, 조회에 실패하면 오류를 던진다 (실패를 "없음"으로 보고 묻지 않고 바꾸지 않도록)
  findMyHold: async () => {
    try {
      const hold = await reservationApi.getMyCurrentHold();
      return hold?.summary ?? null;
    } catch (error) {
      throw toError(error);
    }
  },

  clearDetail: () =>
    set((state) => ({
      detail: null,
      detailStatus: 'idle',
      detailError: null,
      detailRequestId: state.detailRequestId + 1,
      detailClockOffset: 0,
    })),
}));
