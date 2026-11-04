// 결제는 됐지만 예약을 확정할 수 없어 서버가 결제를 바로 전액 취소하는 오류 (docs/api/reservations.md 결제 확인)
const AUTO_CANCELLED_CODES = new Set([
  'RESERVATION_002',
  'RESERVATION_003',
  'RESERVATION_004',
  'RESERVATION_005',
  'VACANCY_002',
]);

// 서버가 결제 여부를 판단하기 전에 끊긴 경우. 결제가 됐을 수 있으므로 예약 상태를 확인하라고 안내한다
// (응답 없음·서버 오류·포트원 연동 오류)
const UNCONFIRMED_CODES = new Set([
  'NETWORK_ERROR',
  'UNKNOWN_ERROR',
  'COMMON_500',
  'PAYMENT_002',
  'HTTP_502',
  'HTTP_504',
]);

// 같은 예약을 다른 탭에서 결제 중이라 결제창을 열지 않은 경우 (서버 PaymentStartService)
const PAYMENT_IN_PROGRESS = 'PAYMENT_008';

/** 결제 확인 실패 안내. 결제가 자동 취소됐는지, 결과를 아직 모르는지 덧붙인다 */
export function verifyErrorMessage(error) {
  if (error.code === PAYMENT_IN_PROGRESS) {
    return `${error.message} 그 화면에서 결제를 마치거나 결제창을 닫은 뒤 다시 시도해 주세요. 그 화면을 닫았다면 3분 뒤에 다시 결제할 수 있어요.`;
  }
  if (AUTO_CANCELLED_CODES.has(error.code)) {
    return `${error.message} 결제한 예약금은 자동으로 취소돼요.`;
  }
  if (UNCONFIRMED_CODES.has(error.code)) {
    return `${error.message} 결제가 진행됐다면 내 예약에서 상태를 확인해 주세요.`;
  }
  return error.message;
}

/**
 * 이 탭(페이지를 연 한 번)을 가리키는 임의 값. 같은 예약을 두 탭에서 결제하지 못하게 서버가 탭을 구분하는 데 쓴다.
 * sessionStorage 는 "탭 복제" 때 함께 복사돼 두 탭이 같은 값을 갖게 되므로 메모리에만 둔다.
 * 모바일 결제창처럼 페이지를 옮길 때는 돌아올 주소에 실어 보낸다.
 */
export const PAYMENT_TAB_ID =
  globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;

// 모바일 결제창은 결과 화면으로 페이지를 옮기므로, 입력한 요청사항을 잠깐 브라우저에 맡겨 결제 확인에 함께 보낸다
const requestKey = (reservationId) => `checkout-request-${reservationId}`;

export function savePendingRequest(reservationId, request) {
  try {
    sessionStorage.setItem(requestKey(reservationId), request);
  } catch {
    // 저장소를 쓸 수 없으면(사생활 보호 모드 등) 모바일 복귀 때 요청사항 없이 확정한다
  }
}

export function takePendingRequest(reservationId) {
  try {
    const request = sessionStorage.getItem(requestKey(reservationId));
    sessionStorage.removeItem(requestKey(reservationId));
    return request || null;
  } catch {
    return null;
  }
}
