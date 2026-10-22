/**
 * 백엔드 ErrorCode → 화면 메시지 매핑.
 * 기준: docs/api/error-codes.md (백엔드 ErrorCode.java). 새 코드가 추가되면 함께 맞춘다.
 */
export const DEFAULT_ERROR_MESSAGE = '알 수 없는 오류가 발생했습니다.';

export const ERROR_MESSAGES = {
  // 클라이언트 측에서 만드는 코드 (서버 응답이 없는 경우)
  NETWORK_ERROR: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.',

  // COMMON
  COMMON_400: '잘못된 요청입니다.',
  COMMON_401: '인증이 필요합니다.',
  COMMON_403: '접근 권한이 없습니다.',
  COMMON_404: '요청한 리소스를 찾을 수 없습니다.',
  COMMON_405: '지원하지 않는 HTTP 메서드입니다.',
  COMMON_500: '서버 내부 오류가 발생했습니다.',
};
